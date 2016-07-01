"use strict";

String.prototype.replaceAll = function (search, replacement) {
    return this.replace(new RegExp(search, 'g'), replacement);
};

angular
    .module('emi.i18n', [])
    .factory('$translator', function ($localStorage) {

        let translator_lang;
        let translation = {};
        let loaded_translations = new Set();

        function set_lang(lang) {
            console.log("set_lang", lang);
            if (translator_lang != lang) {
                translator_lang = lang;
                $localStorage.translator_lang = lang;
                location.reload();
            }
        }

        function is_key(key) {
            return typeof key === "string" && (key.startsWith("%") && key.endsWith("%"));
        }

        function browser_locale() {
            return (window.navigator.userLanguage || window.navigator.language).toLowerCase();
        }

        function get_lang() {
            if (translator_lang == null) {
                translator_lang = $localStorage.translator_lang;
            }
            if (translator_lang == null) {
                if (browser_locale() == "pl") {
                    set_lang(browser_locale());
                } else {
                    set_lang("en");
                }
            }
            return translator_lang;
        }

        function expand(msg, extra) {
            if (extra && extra.length > 0) {
                for (let i = 0; i < extra.length; i++) {
                    let param = extra[i];
                    if (param != null) {
                        if (is_key(param)) {
                            param = translate(param);
                        }
                        msg = msg.replaceAll("\\{" + i + "\\}", param);
                    }
                }
            }
            return msg;
        }

        function _get_translation(dict, key) {
            if (dict == null) {
                return null;
            }
            let car_index = key.indexOf('.');
            if (car_index === -1) {
                return dict[key];
            }
            let car = key.substring(0, car_index);
            let cdr = key.substring(car_index + 1);
            return _get_translation(dict[car], cdr);
        }

        function translate(key) {
            const extra = [].slice.call(arguments, 1);
            if (translator_lang == null) {
                get_lang();
            }

            try {
                if (is_key(key)) {
                    let nkey = key.substring(1, key.length - 1);
                    let message = _get_translation(translation[translator_lang], nkey);
                    if (message != null) {
                        return expand(message, extra);
                    } else {
                        console.warn("missing translation for", key);
                    }
                }
                return expand(key, extra);
            } catch (err) {
                console.error("error", err, "while translating:", key, "extra", extra);
                throw err;
            }
        }

        function i18n_file(file_name) {
            if (translator_lang == null) {
                get_lang();
            }
            return file_name.replaceAll(".html", "_" + translator_lang + ".html")
        }

        function provide(translation_name, local_translation) {
            if (loaded_translations.has(translation_name)) {
                return;
            }
            console.log("adding translation", translation_name);
            loaded_translations.add(translation_name);
            $.extend(true, translation, local_translation);
        }

        return {
            translate,
            provide,
            set_lang,
            get_lang,
            i18n_file,
        }

    });