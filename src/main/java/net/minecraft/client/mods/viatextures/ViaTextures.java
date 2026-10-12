package net.minecraft.client.mods.viatextures;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ViaItems;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;

/**
 * ViaTextures: items newer than 1.14.4 show their real inventory texture/model.
 * <p>
 * A server running ViaVersion + ViaBackwards cannot send a 1.14.4 client an item it does not know, so it sends a
 * stand-in item and tags the stack: a {@code VB|...} NBT key (in the stack tag and/or its {@code display} tag) and,
 * unless the item already had one, a {@code display.Name} holding the real item's English name. This class recognises
 * such stacks and gives the item model code the matching display-only {@link ViaItems} item instead of the stand-in.
 * It needs nothing from the server beyond ViaBackwards (no TuffX+), only affects how the stack is drawn, and is always
 * on - there is deliberately no switch for it.
 */
public final class ViaTextures {

	private static final String VIA_PREFIX = "VB|";
	private static final int MAX_CACHE = 512;

	/** raw display.Name JSON -> resolved item ({@link Items#AIR} = "not a ViaTextures item") */
	private static final Map<String, Item> NAME_CACHE = new HashMap<>();

	private ViaTextures() {
	}

	/**
	 * @return the display-only item whose model should be used to draw this stack, or null to draw the stack's own item
	 */
	public static Item resolve(ItemStack stack) {
		if (stack.isEmpty() || !stack.hasTag()) {
			return null;
		}
		CompoundNBT tag = stack.getTag();
		boolean viaTagged = hasViaKey(tag);
		boolean hasDisplay = tag.contains("display", 10);
		CompoundNBT display = hasDisplay ? tag.getCompound("display") : null;
		if (!viaTagged && display != null) {
			viaTagged = hasViaKey(display);
		}
		// a name alone proves nothing (players can rename items): it must have been put there by ViaBackwards
		if (!viaTagged || display == null || !display.contains("Name", 8)) {
			return null;
		}
		String json = display.getString("Name");
		Item cached = NAME_CACHE.get(json);
		if (cached == null) {
			cached = lookup(json);
			if (NAME_CACHE.size() >= MAX_CACHE) {
				NAME_CACHE.clear();
			}
			NAME_CACHE.put(json, cached);
		}
		return cached == Items.AIR ? null : cached;
	}

	private static boolean hasViaKey(CompoundNBT nbt) {
		for (String key : nbt.keySet()) {
			if (key.startsWith(VIA_PREFIX)) {
				return true;
			}
		}
		return false;
	}

	private static Item lookup(String nameJson) {
		try {
			ITextComponent name = ITextComponent.Serializer.fromJson(nameJson);
			if (name != null) {
				Item item = ViaItems.byEnglishName(name.getString());
				if (item != null) {
					return item;
				}
			}
		} catch (RuntimeException ignored) {
			// not a valid text component: not ours
		}
		return Items.AIR;
	}
}
