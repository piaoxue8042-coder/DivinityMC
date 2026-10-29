package com.px8042.divinitymc.armor;

import com.px8042.divinitymc.DivinityMC;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public final class ArmorAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DivinityMC.MODID);
    public static final Supplier<AttachmentType<ArmorState>> ARMOR = TYPES.register("armor", () ->
        AttachmentType.builder(ArmorState::new).serialize(new IAttachmentSerializer<Tag, ArmorState>() {
            @Override public ArmorState read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider registries) {
                try { return decode(tag); }
                catch (RuntimeException error) {
                    DivinityMC.LOGGER.error("Invalid DivinityMC armor for {}. Quarantined; investigate then /divinitymc reset. Raw NBT: {}", holder, tag, error);
                    return ArmorState.quarantine();
                }
            }
            @Override public Tag write(ArmorState state, HolderLookup.Provider registries) { return encode(state); }
        }).build());

    public static CompoundTag encode(ArmorState state) {
        var s = state.snapshot();
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("version", 1);
        nbt.putDouble("physical", s.physical()); nbt.putDouble("magic", s.magic());
        nbt.putDouble("maxPhysical", s.maxPhysical()); nbt.putDouble("maxMagic", s.maxMagic());
        nbt.putInt("delay", s.recoveryDelay());
        nbt.putBoolean("initialized", s.initialized()); nbt.putBoolean("quarantined", s.quarantined());
        return nbt;
    }
    public static ArmorState decode(Tag tag) {
        if (!(tag instanceof CompoundTag nbt) || !nbt.contains("version", Tag.TAG_INT) || nbt.getInt("version") != 1)
            throw new IllegalArgumentException("Missing/unsupported armor schema");
        for (String key : new String[]{"physical", "magic", "maxPhysical", "maxMagic"})
            if (!nbt.contains(key, Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Missing/invalid " + key);
        if (!nbt.contains("delay", Tag.TAG_INT) || !nbt.contains("initialized", Tag.TAG_BYTE) || !nbt.contains("quarantined", Tag.TAG_BYTE))
            throw new IllegalArgumentException("Missing armor flags");
        return ArmorState.restore(new ArmorState.Snapshot(nbt.getDouble("physical"), nbt.getDouble("magic"),
            nbt.getDouble("maxPhysical"), nbt.getDouble("maxMagic"), nbt.getInt("delay"),
            nbt.getBoolean("initialized"), nbt.getBoolean("quarantined")));
    }
}
