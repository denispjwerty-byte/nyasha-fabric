package ru.nyasha;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Сердце Мелони / Сердце Лисси: призыв, телепорт к себе или создание спутницы. */
public class HeartItem extends Item {
    private final String kind;
    public HeartItem(String kind) { super(new Item.Settings().maxCount(1)); this.kind = kind; }
    public String kind() { return kind; }
    @Override public boolean hasGlint(ItemStack s) { return true; }
    @Override public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
        if (!w.isClient && p instanceof ServerPlayerEntity sp && !sp.getItemCooldownManager().isCoolingDown(this)) {
            sp.getItemCooldownManager().set(this, 30);
            Menu.heart(sp, kind);
        }
        return TypedActionResult.success(p.getStackInHand(h));
    }
}
