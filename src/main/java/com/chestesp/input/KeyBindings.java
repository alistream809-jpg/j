package com.chestesp.input;

import com.chestesp.config.ESPConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

public final class KeyBindings {
    private KeyBindings() {}

    private static final String CATEGORY = "key.categories.chestesp";

    public static KeyBinding MENU;
    public static KeyBinding TOGGLE;
    public static final Map<ESPConfig.Type, KeyBinding> TYPE_KEYS = new EnumMap<>(ESPConfig.Type.class);

    public static void register() {
        MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestesp.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestesp.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        for (ESPConfig.Type t : ESPConfig.Type.values()) {
            TYPE_KEYS.put(t, KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.chestesp.toggle." + t.name().toLowerCase(), InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, CATEGORY)));
        }
    }

    /** All bindings in GUI order. */
    public static KeyBinding[] all() {
        ESPConfig.Type[] ts = ESPConfig.Type.values();
        KeyBinding[] out = new KeyBinding[2 + ts.length];
        out[0] = MENU;
        out[1] = TOGGLE;
        for (int i = 0; i < ts.length; i++) out[2 + i] = TYPE_KEYS.get(ts[i]);
        return out;
    }

    public static String[] names() {
        ESPConfig.Type[] ts = ESPConfig.Type.values();
        String[] out = new String[2 + ts.length];
        out[0] = "Open settings menu";
        out[1] = "Toggle ChestESP";
        for (int i = 0; i < ts.length; i++) out[2 + i] = "Toggle " + ts[i].label;
        return out;
    }
}
