package ru.nyasha;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;
import java.util.regex.*;

/** Порт chat.js: обращение по имени, свободный чат, локальный движок + необязательный LLM. */
public final class Chat {
    private Chat() {}
    private static final String FILLER = "(?:эй|слушай|ну|так|а|о|ой|ах|кстати|дорогая|милая|привет|здарова|здравствуй|хай|ку|пожалуйста)";
    private static final Pattern GREET = Pattern.compile("привет|здарова|здравств|хай|ку");
    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).replace('ё', 'е'); }

    private static final Pattern INSULT = Pattern.compile("дура|тупая|тупиц|идиот|ненавижу|заткнись|урод|бесишь|отстань|уродин");
    private static final Pattern COMPL = Pattern.compile("красив|краси[вш]|милая|умниц|хорошая|прекрасн|молодец|классн|очаровательн|умная");
    private static final Pattern LOVE = Pattern.compile("люблю|обожаю|нравишься|влюблен|влюблён|ты мне дорога");
    private static final Pattern SORRY = Pattern.compile("прости|извини|мой косяк|виноват");
    private static final Pattern MOOD = Pattern.compile("как дела|как ты|как настроение|как самочувствие|что нового");
    private static final Pattern HUG = Pattern.compile("обним|иди сюда|обнимашки");
    private static final Pattern HELLO = Pattern.compile("привет|здарова|здравств|хай|добрый (день|вечер|утро)");

    /** Возвращает текст без обращения или null, если сообщение не ей. */
    static String addressed(ServerPlayerEntity pl, CompanionEntity e, Profile p, String text) {
        Config.Companion comp = Config.COMPANIONS.get(e.kind());
        Set<String> all = new LinkedHashSet<>();
        for (String a : comp.aliases()) all.add(Pattern.quote(norm(a)));
        all.add(Pattern.quote(norm(p.name)));
        String names = String.join("|", all);
        int fl = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern start = Pattern.compile("^\\s*@?\\s*((?:" + FILLER + "[\\s,!.:\\-]+){0,2})(?:" + names + ")(?![а-яa-z])[\\s,:;!.\\-—~]*", fl);
        Pattern sentence = Pattern.compile("(^|[.!?\\n]\\s*)@?\\s*(?:" + names + ")(?![а-яa-z])[\\s,:;!.\\-—~]*", fl);
        Pattern any = Pattern.compile("(^|[^а-яa-z])(?:" + names + ")(?![а-яa-z])[\\s,:;!.\\-]*", fl);
        String t = norm(text);
        double d = e.getWorld() == pl.getWorld() ? e.distanceTo(pl) : Double.POSITIVE_INFINITY;
        Matcher m = start.matcher(t);
        if (m.find()) {
            String rest = text.substring(Math.min(m.end(), text.length())).trim();
            String filler = GREET.matcher(m.group(1)).find() ? m.group(1).replaceAll("[\\s,!.:\\-]+$", "") + " " : "";
            String body = (filler + rest).trim();
            return body.isEmpty() ? "..." : body;
        }
        if (sentence.matcher(t).find()) { String b = sentence.matcher(t).replaceAll("$1").trim(); return b.isEmpty() ? "..." : b; }
        if (any.matcher(t).find() && d <= 24) { String b = any.matcher(t).replaceAll("$1").trim(); return b.isEmpty() ? "..." : b; }
        if (d <= 5 && lookingAt(pl, e)) return text;
        return null;
    }
    private static boolean lookingAt(ServerPlayerEntity pl, CompanionEntity e) {
        var look = pl.getRotationVec(1f);
        var to = e.getEyePos().subtract(pl.getEyePos()).normalize();
        return look.dotProduct(to) > 0.97;
    }

    /** Вызывается из события чата (поток сервера). */
    public static void onMessage(ServerPlayerEntity pl, String text) {
        List<CompanionEntity> near = pl.getServerWorld().getEntitiesByClass(CompanionEntity.class, pl.getBoundingBox().expand(64), c -> pl.getUuid().equals(c.getOwnerId()));
        for (CompanionEntity e : near) {
            Profile p = e.profile();
            if (p == null) continue;
            String body = addressed(pl, e, p, text);
            if (body == null) continue;
            respond(pl, e, p, body);
            return;   // отвечает одна — ближайшая из подходящих
        }
    }

    public static void respond(ServerPlayerEntity pl, CompanionEntity e, Profile p, String body) {
        String t = norm(body);
        long now = e.getWorld().getTime();
        List<String> events = new ArrayList<>();
        Dialogue.Effects eff = new Dialogue.Effects();
        if (INSULT.matcher(t).find()) { events.add("insult"); eff.affection = -4.0; eff.trust = -3.0; eff.irritation = 8.0; p.remember("insult"); e.setEmotion("sad", 160); }
        else if (SORRY.matcher(t).find()) { events.add("apology"); eff.irritation = -6.0; eff.trust = 1.0; }
        else if (LOVE.matcher(t).find()) { events.add("confession"); eff.romance = 3.0; eff.affection = 2.0; e.setEmotion("shy", 160); }
        else if (COMPL.matcher(t).find()) { events.add("compliment"); eff.affection = 3.0; eff.flags_set = List.of("compliment_given"); e.setEmotion("shy", 160); }
        else if (HUG.matcher(t).find()) { events.add("hug"); eff.affection = 2.0; eff.trust = 1.0; }
        else if (MOOD.matcher(t).find()) events.add("ask_mood");
        else if (HELLO.matcher(t).find()) { events.add("ambient"); }
        boolean tone = !events.isEmpty();
        Map<String, Double> ap = tone ? e.change(pl, p, eff, "chat") : new HashMap<>();
        if (Llm.available()) {
            String player = pl.getName().getString();
            Llm.ask(p, player, body).whenComplete((r, ex) -> pl.getServer().execute(() -> {
                if (ex != null || r == null || r.isBlank()) { local(pl, e, p, events, ap); return; }
                Menu.sendLine(pl, e, p, r);
                p.lastSay = e.getWorld().getTime();
            }));
        } else local(pl, e, p, events, ap);
    }

    private static void local(ServerPlayerEntity pl, CompanionEntity e, Profile p, List<String> events, Map<String, Double> ap) {
        if (p.conflict.active && !events.contains("apology")) { Menu.sendLine(pl, e, p, DialogueManager.I.refusal(p)); return; }
        if (!events.isEmpty() && e.fire(pl, events, false, true, null)) return;
        Dialogue.Reaction r = DialogueManager.I.reactionFor(p, ap);
        if (r != null) {
            Menu.sendLine(pl, e, p, DialogueManager.fill(r.text, p, pl.getName().getString()));
            if (r.emotion != null) e.setEmotion(r.emotion, 160);
        } else if (!e.fire(pl, List.of("ambient"), false, true, null)) Menu.sendLine(pl, e, p, "…");
    }
}
