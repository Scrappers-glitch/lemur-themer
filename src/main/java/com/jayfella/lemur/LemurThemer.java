package com.jayfella.lemur;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jayfella.lemur.json.*;
import com.jayfella.lemur.theme.font.AngelFont;
import com.jayfella.lemur.util.BitmapFontUtils;
import com.jayfella.properties.reflection.ReflectedFields;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LemurThemer {

    private static final Logger log = LoggerFactory.getLogger(LemurThemer.class);

    private final ObjectMapper objectMapper;

    private File activeThemeFile;
    private final LemurTheme activeTheme = new LemurTheme();

    public LemurThemer() {

        objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(new SimpleModule()

                        // keep everything in alphabetical order for sanity
                        .addSerializer(ColorRGBA.class, new ColorRGBASerializer(ColorRGBA.class))
                        .addSerializer(IconComponent.class, new IconComponentSerializer(IconComponent.class))
                        .addSerializer(Insets3f.class, new Insets3fSerializer(Insets3f.class))
                        .addSerializer(QuadBackgroundComponent.class, new QuadBackgroundComponentSerializer(QuadBackgroundComponent.class))
                        .addSerializer(TbtQuadBackgroundComponent.class, new TbtQuadBackgroundComponentSerializer(TbtQuadBackgroundComponent.class))
                        .addSerializer(Vector3f.class, new Vector3fSerializer(Vector3f.class))

                        .addDeserializer(Insets3f.class, new Insets3fDeserializer(Insets3f.class))
                        .addDeserializer(IconComponent.class, new IconComponentDeserializer(Insets3f.class))
                        .addDeserializer(TbtQuadBackgroundComponent.class, new TbtQuadBackgroundComponentDeserializer(TbtQuadBackgroundComponent.class))
                        .addDeserializer(QuadBackgroundComponent.class, new QuadBackgroundComponentDeserializer(QuadBackgroundComponent.class))

                );

        // allow the visualization of fields but not getters and setters.
        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));

        // findThemeClasses();
    }

    /**
     * Creates a new instance of all detected classes that extend ThemedElement.
     * @return a Map<themedElement.getClass().getSimpleName(), ThemedElement>
     */
    private Map<String, ThemedElement> createNewThemeClasses() {

        Map<String, ThemedElement> themedElementMap = new HashMap<>();

        log.info("Searching for theme classes...");
        long start = System.currentTimeMillis();

        // Scanning all packages take a LONG time. To alleviate this we specify specific packages to search through.
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                    .forPackages("com.jayfella.lemur.theme"));

        Set<Class<? extends ThemedElement>> classes = reflections.getSubTypesOf(ThemedElement.class);

        long end = System.currentTimeMillis();
        log.info("Took " + (end - start) + " milliseconds to find " +  classes.size() + " theme classes.");

        log.debug("Found ThemedElements: " + classes.toString());

        for (Class<? extends ThemedElement> elementClass : classes) {

            try {
                Constructor<? extends ThemedElement> constructor = elementClass.getConstructor();
                ThemedElement themedElement = constructor.newInstance();

                themedElementMap.put(themedElement.getClass().getSimpleName(), themedElement);

            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
            }

        }

        return themedElementMap;
    }

    /**
     * Sets the theme to the given file.
     *
     * @param themeFile The string representation of the path to the file. E.g. "./themes/mytheme.lemur.json".
     */
    public void setTheme(String themeFile) {
        setTheme(new File(themeFile));
    }

    /**
     * Sets the theme to the given file.
     *
     * @param themeFile The theme file. E.g. new File("./themes/mytheme.lemur.json").
     */
    public void setTheme(File themeFile) {

        //
        Map<String, ThemedElement> themedElementMap = createNewThemeClasses();

        if (!themeFile.exists()) {
            activeTheme.putAll(themedElementMap);

            try {
                objectMapper.writeValue(themeFile, activeTheme);
            } catch (IOException e) {
                throw new RuntimeException("Error writing theme file.", e);
            }
        }
        else {
            LemurTheme loadedTheme;
            try {
                loadedTheme = objectMapper.readValue(themeFile, LemurTheme.class);
            } catch (IOException e) {
                throw new RuntimeException("Error reading theme file.", e);
            }

            // add them if they don't exist in the loaded theme.
            for (Map.Entry<String, ThemedElement> entry : themedElementMap.entrySet()) {
                activeTheme.getThemedElementMap().putIfAbsent(entry.getKey(), entry.getValue());
            }

            activeTheme.setTheme(loadedTheme);
        }

        activeThemeFile = themeFile;

        applyTheme();
    }

    /**
     * Saves all changes made to the active theme.
     */
    public void saveActiveTheme() {

        if (activeThemeFile == null) {
            return;
        }

        try {
            objectMapper.writeValue(activeThemeFile, activeTheme);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Returns the currently active theme, or NULL if there is no active theme.
     *
     * @return the currently active theme, or NULL if there is no active theme.
     */
    public LemurTheme getActiveTheme() {
        return activeTheme;
    }

    /**
     * Returns the file of the currently active theme, or NULL if there is no active theme.
     * @return the currently active theme, or NULL if there is no active theme.
     */
    public File getActiveThemeFile() {
        return activeThemeFile;
    }

    /**
     * Applies all values of the active theme to the Lemur theming system.
     * Changes will only affect newly created Lemur Elements.
     */
    public void applyTheme() {

        Styles styles = GuiGlobals.getInstance().getStyles();
        Attributes attributes;

        for (ThemedElement themedElement : activeTheme.getThemedElementMap().values()) {

            log.debug("Reading values for: " + themedElement.getClass().getSimpleName());

            if (themedElement.getElementId().isEmpty()) {
                attributes = styles.getSelector(activeTheme.getName());
                log.debug("Setting root values.");
            }
            else {

                if (themedElement.getChild().isEmpty()) {
                    attributes = styles.getSelector(themedElement.getElementId(), activeTheme.getName());
                    log.debug("Setting values for: " + themedElement.getElementId());
                }
                else {
                    attributes = styles.getSelector(themedElement.getElementId(), themedElement.getChild(), activeTheme.getName());
                    log.debug("Setting values for: " + themedElement.getElementId() + " / " + themedElement.getChild());
                }

            }

            Collection<Field> fields = ReflectedFields.getAllDeclaredFields(themedElement.getClass());

            // find out if textShadow is enabled on this element.
            boolean textShadowEnabled = textShadowEnabled(fields, themedElement);

            for (Field field : fields) {

                boolean accessible = field.isAccessible();

                if (!accessible) {
                    field.setAccessible(true);
                }

                try {

                    String name = field.getName();
                    Object value = field.get(themedElement);

                    // special case for font files.
                    if (value instanceof AngelFont) {
                        AngelFont angelFont = (AngelFont) value;
                        value = BitmapFontUtils.fromAngelFont(angelFont);
                    }

                    // text shadow
                    if (!textShadowEnabled && (name.equals("shadowColor") || name.equals("shadowOffset"))) {
                        // if we enable it, update, then disable, we need to change the values to null.
                        attributes.set("shadowColor", null);
                        attributes.set("shadowOffset", null);
                    }

                    log.debug("Setting value '" + name + "' to " + value);

                    attributes.set(name, value);

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                if (!accessible) {
                    field.setAccessible(false);
                }

            }

        }

        // finally
        GuiGlobals.getInstance().getStyles().setDefaultStyle(activeTheme.getName());

    }

    /**
     * Searches the given fields of a class to check if text shadowing is enabled.
     * @param fields the fields to scan.
     * @return whether or not a field names "textShadow" exists and has been set to true.
     */
    private boolean textShadowEnabled(Collection<Field> fields, Object parent) {

        Field textShadowField = fields.stream()
                .filter(field -> field.getName().equals("textShadow"))
                .findFirst()
                .orElse(null);

        if (textShadowField != null) {

            boolean accessible = textShadowField.isAccessible();

            if (!accessible) {
                textShadowField.setAccessible(true);
            }

            try {
                return (boolean) textShadowField.get(parent);
            } catch (IllegalAccessException e) {
                log.error("Unable to determine textShadow value.", e);
            }

            if (!accessible) {
                textShadowField.setAccessible(false);
            }

            return false;

        }

        return false;
    }

}
