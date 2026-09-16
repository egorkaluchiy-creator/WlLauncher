package net.legacylauncher.configuration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.util.Lazy;
import net.legacylauncher.util.U;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class LangConfiguration {
    public static final Locale ru_RU = U.getLocale("ru_RU");
    private static final String LOCALE_PATH = "/net/legacylauncher/lang";

    private static final String[] KNOWN_LOCALES = {
            "ru_RU", "en_US"
    };

    private static final Lazy<List<Locale>> localeList = Lazy.of(() -> {
        Set<Locale> foundLocales = new LinkedHashSet<>();

        try {
            URL url = LangConfiguration.class.getResource(LOCALE_PATH);
            if (url != null) {
                final Function<Stream<Path>, List<Locale>> locator = stream -> stream
                        .map(it -> it.getFileName().toString())
                        .filter(it -> it.startsWith("lang_") && it.endsWith(".properties"))
                        .map(it -> it.substring("lang_".length(), it.length() - ".properties".length()).replace('_', '-'))
                        .map(Locale::forLanguageTag)
                        .sorted(Comparator.comparing(Locale::toString))
                        .collect(Collectors.toList());

                URI uri = url.toURI();
                if (uri.getScheme().equals("jar")) {
                    String spec = uri.getRawSchemeSpecificPart();
                    int separator = spec.indexOf("!/");
                    if (separator != -1) {
                        URI jarUri = URI.create("jar:" + spec.substring(0, separator));
                        FileSystem fs;
                        boolean created = false;
                        try {
                            fs = FileSystems.getFileSystem(jarUri);
                        } catch (FileSystemNotFoundException e) {
                            fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap(), LangConfiguration.class.getClassLoader());
                            created = true;
                        }
                        try (Stream<Path> stream = Files.walk(fs.getPath(LOCALE_PATH), 1).skip(1)) {
                            foundLocales.addAll(locator.apply(stream));
                        } finally {
                            if (created) {
                                fs.close();
                            }
                        }
                    }
                } else {
                    try (Stream<Path> stream = Files.walk(Paths.get(uri), 1).skip(1)) {
                        foundLocales.addAll(locator.apply(stream));
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("Failed dynamic locale discovery: {}", t.toString());
        }

        // Always check and add known locales if resources exist
        for (String locName : KNOWN_LOCALES) {
            try {
                if (LangConfiguration.class.getResource(LOCALE_PATH + "/lang_" + locName + ".properties") != null
                        || LangConfiguration.class.getResourceAsStream(LOCALE_PATH + "/lang_" + locName + ".properties") != null) {
                    foundLocales.add(U.getLocale(locName));
                }
            } catch (Exception ignored) {
            }
        }

        if (foundLocales.isEmpty()) {
            log.warn("Fallback to default locale set (ru_RU, en_US)");
            foundLocales.add(ru_RU);
            foundLocales.add(Locale.US);
        }

        List<Locale> result = new ArrayList<>(foundLocales);
        result.sort(Comparator.comparing(Locale::toString));
        log.info("Available locales ({}): {}", result.size(), result);
        return Collections.unmodifiableList(result);
    });

    private final Map<Locale, Properties> translationsMap = new HashMap<>();
    private final Map<Locale, Pattern[]> pluralMap = new HashMap<>();
    @Getter
    private Locale locale;

    public LangConfiguration() {
        setLocale(Locale.US);
    }

    private static String[] checkVariables(Object[] check) {
        if (check == null || check.length == 1 && check[0] == null) {
            return new String[0];
        } else {
            String[] string = new String[check.length];
            for (int i = 0; i < check.length; ++i) {
                string[i] = String.valueOf(check[i]);
            }
            return string;
        }
    }

    public static List<Locale> getAvailableLocales() {
        return localeList.get();
    }

    public String lget(Locale locale, String key) {
        if (key == null) {
            return null;
        }
        if (translationsMap.containsKey(locale)) {
            Properties l = translationsMap.get(locale);
            return l.getProperty(key);
        }
        return null;
    }

    public String lget(Locale locale, String key, Object... vars) {
        if (key == null) {
            return null;
        }

        String value = lget(locale, key);
        if (key.equals(value) || StringUtils.isEmpty(value)) {
            return null;
        }

        String[] variables = checkVariables(vars);
        Pattern[] plurals = pluralMap.computeIfAbsent(locale, this::getPluralPatterns);

        if (plurals == null || plurals.length == 0) {
            return value;
        }

        for (int var = 0; var < variables.length; var++) {
            String pluralReplacementValue = nget(key + '.' + var + ".plural");
            if (pluralReplacementValue == null) {
                value = StringUtils.replace(value, "%" + var, variables[var]);
                continue;
            }

            String[] pluralReplacements = StringUtils.split(pluralReplacementValue, ';');
            for (int patternKey = 0; patternKey < plurals.length; patternKey++) {
                if (plurals[patternKey].matcher(variables[var]).matches()) {
                    value = StringUtils.replace(value, "%" + var, StringUtils.replace(pluralReplacements[patternKey], "%n", variables[var]));
                    break;
                }
            }
        }

        return value;
    }

    public String nget(String key) {
        return lget(locale, key);
    }

    public String get(String key) {
        String value = nget(key);
        if (value == null) {
            value = lget(selectBackingLocale(), key);
        }
        if (value == null) {
            value = lget(ru_RU, key);
        }
        if (value == null) {
            value = lget(Locale.US, key);
        }
        return value == null ? key : value;
    }

    public String nget(String key, Object... vars) {
        return lget(locale, key, vars);
    }

    public String get(String key, Object... vars) {
        String value = nget(key, vars);
        if (value == null) {
            value = lget(selectBackingLocale(), key, vars);
        }
        if (value == null) {
            value = lget(ru_RU, key, vars);
        }
        if (value == null) {
            value = lget(Locale.US, key, vars);
        }
        return value == null ? key : value;
    }

    private Locale selectBackingLocale() {
        if (locale != ru_RU && Configuration.isLikelyRussianSpeakingLocale(locale != null ? locale.toString() : "")) {
            loadLocale(ru_RU);
            return ru_RU;
        } else {
            loadLocale(Locale.US);
            return Locale.US;
        }
    }

    public synchronized void setLocale(Locale locale) {
        this.locale = locale;

        if (locale == null) {
            log.warn("Tried to set locale to null");
            this.locale = Locale.US;
        }

        loadLocale(this.locale);
        // Preload fallback locales
        loadLocale(Locale.US);
        if (Configuration.isLikelyRussianSpeakingLocale(this.locale.toString())) {
            loadLocale(ru_RU);
        }
    }

    private synchronized void loadLocale(Locale locale) {
        if (locale == null || translationsMap.containsKey(locale)) {
            return;
        }
        Properties translations = getTranslations(locale);
        if (translations != null) {
            translationsMap.put(locale, translations);
            checkConsistency(locale);
        }
    }

    private Properties getTranslations(Locale localeObj) {
        if (localeObj == null) {
            return null;
        }

        String localeStr = localeObj.toString();
        if (localeStr.equals("id_ID") || localeStr.equals("id")) {
            localeStr = "in_ID";
        }

        Properties translations = translationsMap.get(localeObj);
        if (translations != null) {
            return translations;
        }

        InputStream in = LangConfiguration.class.getResourceAsStream(LOCALE_PATH + "/lang_" + localeStr + ".properties");
        if (in == null && localeStr.contains("_")) {
            String langOnly = localeStr.substring(0, localeStr.indexOf('_'));
            in = LangConfiguration.class.getResourceAsStream(LOCALE_PATH + "/lang_" + langOnly + ".properties");
        }
        if (in == null) {
            for (String kl : KNOWN_LOCALES) {
                if (kl.startsWith(localeStr + "_") || kl.equalsIgnoreCase(localeStr)) {
                    in = LangConfiguration.class.getResourceAsStream(LOCALE_PATH + "/lang_" + kl + ".properties");
                    if (in != null) break;
                }
            }
        }

        if (in != null) {
            try (InputStream stream = in) {
                translations = SimpleConfiguration.loadFromStream(stream);
                log.info("Successfully loaded {} translations for locale {}", translations.size(), localeStr);
                return translations;
            } catch (Exception e) {
                log.warn("Could not load translations for {}", localeStr, e);
                return null;
            }
        }

        log.warn("Could not find translation resource for {}", localeStr);
        return null;
    }

    private Pattern[] getPluralPatterns(Locale locale) {
        Properties translations = getTranslations(locale);
        if (translations == null) {
            return null;
        }

        String pluralFormsRaw = translations.getProperty("plural");
        if (pluralFormsRaw == null) {
            log.warn("Plural forms not found: {}", locale);
            return null;
        }

        String[] pluralFormsSplit = StringUtils.split(pluralFormsRaw, ';');
        Pattern[] pluralForms = new Pattern[pluralFormsSplit.length];

        for (int i = 0; i < pluralForms.length; i++) {
            try {
                pluralForms[i] = Pattern.compile(pluralFormsSplit[i]);
            } catch (PatternSyntaxException sE) {
                throw new IllegalArgumentException("\"" + pluralFormsSplit[i] + "\" is not valid pattern; check plural forms for " + locale, sE);
            }
        }

        return pluralForms;
    }

    private void checkConsistency(Locale locale) {
        if (LegacyLauncher.getInstance() == null || !LegacyLauncher.getInstance().isDebug() || locale == ru_RU) {
            return;
        }

        Properties translations = getTranslations(locale);
        if (translations == null) {
            return;
        }

        Properties ruTranslations = getTranslations(ru_RU);
        if (ruTranslations != null) {
            for (Object key : ruTranslations.keySet()) {
                if (!translations.containsKey(key)) {
                    log.warn("{} is missing key: {}", locale, key);
                }
            }
            for (Object key : translations.keySet()) {
                if (!ruTranslations.containsKey(key)) {
                    log.warn("{} has redundant key: {}", locale, key);
                }
            }
        }
    }
}
