angular
    .module('sbApp', [
        'ngMaterial',
        'ngFileUpload',
        'ngSanitize',
        'ngRoute',
        'ngAnimate',
        'ngStorage',
        'ngCookies',
        'angularMoment',
        'vjs.video',
        'zxcvbn',
        'vcRecaptcha',
        'duScroll',
        'emi.i18n',
        'emi.admin',
        'emi.utils',
        'emi.contact',
        'emi.payments',
        'emi.loginHistory',
        'emi.summary',
        'emi.preview',
        'emi.notifs',
        'emi.main',
        'emi.resetPasswd',
        'emi.msgs',
        'emi.trigger',
        'emi.files',
        'emi.recording',
        'emi.fw',
        'emi.account',
        'emi.sec.2fa',
        'emi.sec.encKey',
        'emi.sec.changePasswd',
        'emi.signUp',
        'emi.login'])
    .factory('MyStart', function ($q, MyUserService, $rootScope, $location, $translator, MyUtils, $timeout, $interval, MyDialog) {

        function nagUser() {
            if (!$rootScope.user.tosAccepted) {
                $rootScope.displayTos(null);
            }

            if (!$rootScope.nagInstalled) {
                $rootScope.nagInstalled = true;

                function g() {
                    if ($rootScope.user && $rootScope.user.tosAccepted && $rootScope.user.needPayment) {
                        $rootScope.displayPay(null);
                    }
                }

                function f() {
                    delete $rootScope.nagInstalled_timer;
                    g();
                    $rootScope.nagInstalled = $interval(g, 10000);
                }

                $rootScope.nagInstalled_timer = $timeout(f, 3000);
            }

        }

        function refreshUserDataLoginNotRequired() {
            console.log("refreshUserDataLoginNotRequired");

            $rootScope.curr_scope = null;

            if ($rootScope.user == null) {
                console.log("no user data in $rootScope, checking if happen to be able to get one");

                return MyUserService.current().then(
                    function (user) {
                        $rootScope.user = user;
                        nagUser();
                        // in the preview mode, page language is the one define by the message
                        if (!$rootScope.isPreview()) {
                            $translator.set_lang($rootScope.user.lang);
                        }
                    }, function () {
                        MyUtils.cancel_display_generic_error();
                        return $q.resolve();
                    });
            }
            return $q.resolve();
        }

        function refreshUserData(forceReload) {
            console.log("refreshUserData forceReload=", forceReload);

            $rootScope.curr_scope = null;

            if ($rootScope.user == null || forceReload) {

                if ($rootScope.user == null) {
                    console.log("no user data in $rootScope");
                } else {
                    console.log("forceReload to get user data");
                }

                return MyUserService.current().then(
                    function (user) {
                        $rootScope.user = user;
                        nagUser();

                        // in the preview mode, page language is the one define by the message
                        if (!$rootScope.isPreview()) {
                            $translator.set_lang($rootScope.user.lang);
                        }
                    }, function (err) {
                        MyUtils.cancel_display_generic_error();
                        $location.path("/");
                        return $q.reject(err);
                    });
            }
            return $q.resolve();
        }

        return {
            refreshUserDataLoginNotRequired,
            refreshUserData
        }

    })
    .factory('MyHttp', function ($q, $http, $rootScope, $location, $window, MyUtils) {

        function decLoading() {
            // console.log("decLoading", $rootScope.loading);
            $rootScope.loading--;
            if ($rootScope.loading < 0) {
                throw "$loading < 0";
            }
        }

        function incLoading() {
            // console.log("incLoading", $rootScope.loading);
            if ($rootScope.loading == null) {
                $rootScope.loading = 0;
            }
            $rootScope.loading++;
        }


        function on_error(err) {
            decLoading();

            //unauthorized
            if (err.status == 401) {
                delete $rootScope.user;
            } else if (err.status == 503) {
                delete $rootScope.user;
                $location.path("/");
                return "Our servers are experiencing issues, please come back later";
            }

            return MyUtils.extract_error_mesage(err);
        }

        let curr_reqs = new WeakMap();

        function do_post(url, data) {
            url = "/api" + url;
            console.log("POST", url, data);
            incLoading();
            let t_start = new Date().getTime();
            return $http.post(url, data).then(function (data) {
                console.log(url, "- done in " + (new Date().getTime() - t_start) + "ms", data.data);
                decLoading();
                return data.data;
            }, function (err) {
                console.warn(url, "- POST request error", err);
                err = on_error(err);

                MyUtils.show_critical_with_delay("%action_failed%", err);

                return $q.reject(err);
            });
        }

        function do_get(url) {
            url = "/api" + url;
            console.log("GET", url);
            incLoading();
            let t_start = new Date().getTime();
            return $http.get(url).then(function (data) {
                console.log(url, "- done in " + (new Date().getTime() - t_start) + "ms", data.data);
                decLoading();
                return data.data;
            }, function (err) {
                console.warn(url, "- GET request error", err);
                err = on_error(err);

                MyUtils.show_critical_with_delay("%action_failed%", err);

                return $q.reject(err);
            });
        }

        class Builder {

            constructor() {
                this.m = {};
            }

            set_prefix(prefix) {
                this.prefix = prefix;
                return this;
            }

            add_get_method(service_name) {
                const that = this;
                this.m[service_name] = function () {
                    return do_get(that.prefix + service_name);
                };
                return this;
            }

            add_post_method(service_name) {
                const that = this;
                this.m[service_name] = function (data) {
                    return do_post(that.prefix + service_name, data);
                };
                return this;
            }

            build() {
                return this.m;
            }
        }

        return {
            builder: function () {
                return new Builder()
            }
        }

    })
    .factory('MyManageService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/manage/")
            .add_get_method("info")
            .build();
    })
    .factory('MyLoginService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/")
            .add_post_method("login")
            .add_post_method("preregister")
            .add_post_method("register")
            .add_post_method("send_message")
            .add_post_method("reset_passwd")
            .add_post_method("recall_totp_secret")
            .add_post_method("change_passwd_by_reset_key")
            .build();
    })
    .factory('MyNotifService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/notif/")
            .add_get_method("all")
            .add_post_method("mark_as_read")
            .build();
    })
    .factory('MyUserService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/user/")
            .add_get_method("current")
            .add_post_method("logout")
            .add_post_method("enable_2fa")
            .add_post_method("disable_2fa")
            .add_post_method("update_user")
            .add_post_method("delete_user")
            .add_post_method("request_for_storage")
            .add_post_method("change_passwd")
            .add_post_method("get_aes_key")
            .add_post_method("set_aes_key")
            .add_post_method("generate_totp_secret")
            .add_post_method("send_x_notification")
            .add_post_method("send_y_notification")
            .add_post_method("send_z_notification")
            .add_post_method("get_login_history")
            .add_post_method("get_bitcoin_address")
            .add_get_method("get_qr")
            .build();
    })
    .factory('MyPreviewService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/preview/")
            .add_post_method("decrypt")
            .add_post_method("get_by_uuid")
            .build();
    })
    .factory('MyAdminService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/admin/")
            .add_get_method("stats")
            .add_get_method("metrics")
            .add_post_method("with_status")
            .add_post_method("get_user_encrypted_encryption_key")
            .add_post_method("issue_reject")
            .add_post_method("issue_resolve")
            .build();
    })
    .factory('MyMessageService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/msg/")
            .add_get_method("get_abstract")
            .add_post_method("add_msg")
            .add_post_method("decrypt")
            .add_post_method("set_password")
            .add_post_method("remove_password")
            .add_post_method("load_msg")
            .add_post_method("update_msg")
            .add_post_method("delete_msg")
            .build();
    })
    .factory('MyFileService', function (MyHttp) {
        return MyHttp.builder()
            .set_prefix("/file/")
            .add_post_method("delete_file")
            .add_post_method("encrypt")
            .add_post_method("decrypt")
            .add_post_method("get_files")
            .build();
    })
    .factory('MyDialog', function ($mdDialog, $q, $translator) {

        $translator.provide("MyDialog.factory", {
            pl: {
                confirm: {
                    action: "Zatwierdź akcje",
                    yes: "Tak",
                    no: "Nie",
                },
            }, en: {
                confirm: {
                    action: "Confirm action",
                    yes: "Yes",
                    no: "No",
                },
            }
        });

        function isDialogOpen() {
            return angular.element(document).find('md-dialog').length > 0;
        }

        function closeDialog() {
            return $mdDialog.cancel();
        }

        function showBasicDialog(event, opts, scope) {
            console.log("show dialog:", opts);

            const modalScope = scope.$new(false);
            modalScope.closeDialog = closeDialog;

            let p = $mdDialog.show(Object.assign({
                parent: angular.element(document.body),
                targetEvent: event,
                clickOutsideToClose: true,
                // disableParentScroll: false,
                scope: modalScope
            }, opts));

            if (opts.onClose) {
                p.then(function () {
                    console.log("dialog onclose#1");
                    opts.onClose();
                }, function (err) {
                    console.log("dialog onclose#2");
                    opts.onClose(err);
                    return $q.reject(err);
                });
            }

            return modalScope;
        }

        function showConfirm(event, content) {

            const confirm = $mdDialog.confirm()
                .title($translator.translate("%confirm.action%"))
                .htmlContent(content)
                .targetEvent(event)
                .ok($translator.translate("%confirm.yes%"))
                .cancel($translator.translate("%confirm.no%"));

            return $mdDialog.show(confirm);
        }

        function showAlert(event, content) {

            const confirm = $mdDialog.alert()
                .title("Information")
                .htmlContent(content)
                .targetEvent(event)
                .ok("Ok");

            return $mdDialog.show(confirm);
        }

        return {
            showBasicDialog,
            showConfirm,
            showAlert,
            closeDialog,
            isDialogOpen
        }
    })
    .directive('i18n', function () {
        return {
            template: function (elem, attr) {
                return `<span ng-bind-html='i18n("${attr.key}", ${attr.args}) | trustAsHtml'/>`
            },
            replace: true
        };
    })
    .directive('zxPasswordMeter', function ($translator) {
        return {
            scope: {
                value: "@",
                max: "@?"
            },
            templateUrl: "password-meter.html",
            link: function (scope) {
                scope.type = 'danger';
                scope.max = (!scope.max) ? 100 : scope.max;

                scope.$watch('value', function (newValue) {
                    var strenghPercent = newValue / scope.max;

                    scope.strenghPercent = strenghPercent * 100;
                    if (strenghPercent === 0) {
                        scope.text = $translator.translate('%passwd.awful%');
                    } else if (strenghPercent <= 0.25) {
                        scope.type = 'danger';
                        scope.text = $translator.translate('%passwd.weak%');
                    } else if (strenghPercent <= 0.50) {
                        scope.type = 'warning';
                        scope.text = $translator.translate('%passwd.moderate%');
                    } else if (strenghPercent <= 0.75) {
                        scope.type = 'warning';
                        scope.text = $translator.translate('%passwd.strong%');
                    } else {
                        scope.type = 'success';
                        scope.text = $translator.translate('%passwd.perfect%');
                    }

                });
            }
        }
    })
    .filter('capitalize', function() {
        return function(input) {
            return (!!input) ? input.charAt(0).toUpperCase() + input.substr(1).toLowerCase() : '';
        }
    })
    .filter('asDate', function () {
        return function (val) {
            return moment(val).format("YYYY/MM/DD HH:mm Z");
        }
    })
    .filter('encodeUri', function ($window) {
        return $window.encodeURIComponent;
    })
    .filter('trustAsHtml', function ($sce) {
        return $sce.trustAsHtml;
    })
    .filter('bytes', function () {
        return function (bytes, precision) {
            if (bytes === 0) {
                return '0 bytes'
            }
            if (isNaN(parseFloat(bytes)) || !isFinite(bytes)) return '-';
            if (typeof precision === 'undefined') precision = 1;

            var units = ['bytes', 'kB', 'MB', 'GB', 'TB', 'PB'],
                number = Math.floor(Math.log(bytes) / Math.log(1024)),
                val = (bytes / Math.pow(1024, Math.floor(number))).toFixed(precision);

            return (val.match(/\.0*$/) ? val.substr(0, val.indexOf('.')) : val) + ' ' + units[number];
        }
    })
    .filter('reverse', function () {
        return function (items) {
            if (!items) {
                return items;
            }
            return items.slice().reverse();
        };
    })
    .config(function ($routeProvider) {

        var originalWhen = $routeProvider.when;

        $routeProvider.when = function (path, route) {

            route.resolve = {
                get_logged_user_data: [
                    'MyStart',
                    function (MyStart) {
                        if (route.login_not_required)
                            return MyStart.refreshUserDataLoginNotRequired();
                        else {
                            return MyStart.refreshUserData();
                        }
                    }
                ]
            };
            return originalWhen.call($routeProvider, path, route);
        };

        $routeProvider.when('/reset_passwd', {
            templateUrl: 'login/resetPasswd.html',
            controller: 'resetPasswdCtrl',
            login_not_required: true,
        }).when('/tos', {
            templateUrl: 'tos/tos.html',
            login_not_required: true,
        }).when('/security', {
            templateUrl: 'security/security.html',
            login_not_required: true,
        }).when('/contact', {
            templateUrl: 'contact/contact.html',
            controller: 'contactCtrl',
            login_not_required: true,
        }).when('/faq', {
            templateUrl: 'faq/faq.html',
            login_not_required: true,
        }).when('/msgs', {
            templateUrl: 'msgs/msgs.html',
            controller: 'msgsCtrl'
        }).when('/files', {
            templateUrl: 'files/files.html',
            controller: 'attaCtrl'
        }).when('/account', {
            templateUrl: 'account/account.html',
            controller: 'accountCtrl',
        }).when('/admin', {
            templateUrl: 'admin/admin.html',
            controller: 'adminCtrl',
        }).when('/trigger', {
            templateUrl: 'trigger/trigger.html',
            controller: 'triggerCtrl',
        }).when('/preview', {
            templateUrl: 'preview/preview.html',
            controller: 'previewCtrl',
            login_not_required: true,
        }).when('/notifs', {
            templateUrl: 'notifs/notifs.html',
            controller: 'notifsCtrl',
        }).when('/summary', {
            templateUrl: 'summary/summary.html',
            controller: 'summaryCtrl',
        }).when('/', {
            templateUrl: 'main/main.html',
            controller: 'mainCtrl',
            login_not_required: true,
        }).otherwise({
            redirectTo: '/'
        });
    })
    .config(['$mdAriaProvider', function ($mdAriaProvider) {
        $mdAriaProvider.disableWarnings();
    }])
    .run(function ($rootScope, $location, $timeout, MyUtils, MyDialog, $translator, $cookies, $document, MyManageService) {

        let lang = $translator.get_lang();

        console.log("Welcome to The Machine", lang);
        moment.locale(lang);

        MyManageService.info().then(function (gitInfo) {
            $rootScope.gitInfo = gitInfo;
        });

        navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

        function scroll_to(id) {
            let someElement = angular.element(document.getElementById(id));
            if (someElement.length == 0) {
                $timeout(scroll_to.bind(null, id), 50);
            } else {
                var duration = 500; //milliseconds
                var offset = 30; //pixels; adjust for floating menu, context etc
                //Scroll to #some-id with 30 px "padding"
                //Note: Use this in a directive, not with document.getElementById
                $document.scrollToElement(someElement, offset, duration);
            }
        }

        $rootScope.current_page = function () {
            return $location.path();
        };

        $rootScope.open_page = function (path) {
            let b = path;
            let hash = null;

            let i = path.indexOf("#");
            if (i != -1) {
                b = path.substring(0, i);
                hash = path.substring(i + 1);
            }
            console.log("open_page", b, hash, path);
            $location.path(b);
            if (hash != null && hash.length > 0) {
                $location.hash(hash);
            } else {
                $location.hash("");
            }
            if (hash) {
                scroll_to(hash);
            }
        };

        $rootScope.displayPay = function ($event, force) {
            console.log("displayPay");

            let current_page = $rootScope.current_page();
            let is_logged_page = current_page === '/msgs' || current_page === '/files' || current_page === '/trigger' || current_page === '/summary' || current_page === '/notifs';
            if (!is_logged_page && !force) {
                return;
            }

            if (!$rootScope.displayPay.dialog) {

                $rootScope.displayPay.dialog = true;

                MyDialog.showBasicDialog($event, {
                    templateUrl: '/account/payments.dialog.html',
                    clickOutsideToClose: false,
                    escapeToClose: false,
                    fullscreen: true,
                    onClose: function () {
                        console.log("payment nag closed");

                        $rootScope.displayPay.dialog = false;
                    }
                }, $rootScope);

            } else {
                console.log("payment already shown")
            }

        };

        $rootScope.displayTos = function ($event) {
            console.log("displayTos");

            MyDialog.showBasicDialog($event, {
                templateUrl: '/tos/tos.dialog.html',
                clickOutsideToClose: false,
                escapeToClose: false,
                fullscreen: true,
            }, $rootScope);

        };

        $rootScope.csrfToken = function () {
            return $cookies.get("XSRF-TOKEN")
        };

        $rootScope.i18n_file = $translator.i18n_file;
        $rootScope.i18n = $translator.translate;
        $rootScope.isAdmin = function () {
            return $rootScope.user && $rootScope.user.role == "admin";
        };

        $rootScope.moment = moment;
        $rootScope.MyUtils = MyUtils;

        $rootScope.isPreview = function () {
            return $location.path().startsWith("/preview");
        };

        $rootScope.isMain = function () {
            return $location.path() == '/';
        };

        $rootScope.$on("$locationChangeStart", function (event, nextUrl, currentUrl) {
            console.log("on $locationChangeStart", currentUrl, nextUrl);

            if (currentUrl !== nextUrl) {
                if (Object.keys($location.search()).length > 0) {
                    console.log("clear GET params");
                    $location.search({});
                }
            }

            if (MyDialog.isDialogOpen()) {
                console.log("dialog is present, closing it");
                MyDialog.closeDialog();
                event.preventDefault();
            }

        });

        $translator.provide("default", {
            pl: {
                i_accept: "Akceptuje",
                passwd: {
                    awful: "bardzo słabe",
                    weak: "słabe",
                    moderate: "średnie",
                    strong: "silne",
                    perfect: "doskonałe"
                },
                unread_notifications: "Masz <b>{0}</b> nieprzeczytanych notyfikacji",
                show: "pokaż",
                action_failed: "Operacja się nie powiodła: <b>{0}</b>",
                account_activation_required: "Aby włączyć wyzwalacz musisz aktywowa swoje konto. Nieaktywne konto zostanie usunięte po godzinie od daty ostatniej aktywności.",

                password: "Hasło",
                password_is_required: "Aby wykonać tę operacje musisz podać hasło",
                password_retype: "Powtórz hasło",
                passwords_not_the_same: "Podane hasła nie są takie same",

                aes_key_missing_title: "Brak klucza szyfrowania, edycja wiadomości nie jest możliwa",
                aes_key: "Klucz szyfrowania",
                settings_has_been_updated: "Zapisano",
                time_unit: {
                    WEEKS: "tygodnie",
                    DAYS: "dni",
                    HOURS: "godziny",
                    MINUTES: "minuty",
                },
                menu: {
                    home: "Strona główna",
                    _2fa: "Uwierzytelnianie wielopoziomowe",
                    payments: "Płatności",
                    security: "Bezpieczeństwo",
                    how_it_works: "Jak to działa",
                    faq: "Pomoc / FAQ",
                    files: "Pliki",
                    pricing: "Cennik",
                    aes_keys: "Klucz AES",
                    login_history: "Historia logowania",

                    notifs: "Notyfikacje",
                    messages: "Wiadomości",
                    messages_caption: "Tworzenie, edycja, zarządzanie wiadomościami",

                    attachments: "Pliki",
                    attachments_caption: "Zarządzanie plikami i nagraniami",

                    trigger: "Wyzwalacz",
                    trigger_caption: "Konfiguracja kiedy Wiadomości zostaną wysłane",

                    summary: "Podsumowanie",
                    summary_caption: "Zestawienie tego co zostanie wykonane przez system",

                    account: "Ustawienia konta",
                    account_caption: "Ustawienia konta",

                    logout: "Wyloguj sie",
                    login: "Zaloguj się",
                    create_account: "Utwórz konto",
                    contact: "Kontakt",
                    tos: "Regulamin",
                },
                msg: {
                    folder: {
                        outbox: "Do wysłania",
                        drafts: "Wersje robocze",
                    },
                    decrypt: {
                        caption: "Ta wiadomość jest zaszyfrowana, podaj hasło aby ją otworzyć",
                        success: "Wiadomość została odszyfrowana",
                        fail: "Nie udało się odszyfrować wiadomości: <b>{0}</b>",
                    },
                    validation: {
                        undefined_recipients: "brak adresatów"
                    },
                    from: "Od",
                    to: "Do",
                    title: "Temat",
                    no_title: "brak tematu",
                    creation_date: "Data utworzenia",
                    modification_date: "Data ostatniej modyfikacji",
                    attachments: "Załączniki",
                    edit: "Edycja"
                },
                file: {
                    folder: {
                        file_misc: "Przesłane pliki",
                        vid_new: "Nowe nagrania",
                        file_encrypted: "Pliki zaszyfrowane",
                    },
                },
                btn: {
                    ok: "Ok",
                    cancel: "Anuluj",
                    confirm: "Zatwierdź",
                    close: "Zamknij",
                    back: "Powrót",
                    save: "Zapisz",
                    send: "Wyślij",
                    move_to: "Przenieś do",
                    encrypt: "Zaszyfruj",
                    decrypt: "Odszyfruj",
                    open: "Otwórz",
                    more: "Więcej",
                    remove: "Usuń",
                    change_passwd: "Zmień hasło",
                    preview: "Podgląd",
                    discard: "Odrzuć",
                    attach_files: "Załącz pliki",
                    record_video: "Nagraj wideo",
                    done: "Zakończ",
                },

                email_addr: "Adres e-mail",
                language: "Język",
                hint: "Wskazówka",
                warning: "Uwaga",
                insert_aes_key: "Podaj klucz szyfrowania",

                fw: {
                    upload_time: "Przesłano",
                    file_size: "Rozmiar",
                    recording_length: "Długość nagrania"
                }

            },
            en: {
                i_accept: "I accept",
                passwd: {
                    awful: "awful",
                    weak: "weak",
                    moderate: "moderate",
                    strong: "strong",
                    perfect: "perfect"
                },
                unread_notifications: "You have <b>{0}</b> unread notification(s)",
                show: "show",
                action_failed: "Operation failed: <b>{0}</b>",
                account_activation_required: "To activate the trigger you have to activate your account first. Not activated account will be removed after an hour from the date of last activity.",

                password: "Password",
                password_is_required: "To perform this operation you must enter your password",
                password_retype: "Repeat password",
                passwords_not_the_same: "The passwords are not the same",

                aes_key_missing_title: "Encryption Key has been removed, message editing is not possible",
                aes_key: "Encryption Key",
                settings_has_been_updated: "Saved",

                time_unit: {
                    WEEKS: "weeks",
                    DAYS: "days",
                    HOURS: "hours",
                    MINUTES: "minutes",
                },
                menu: {
                    home: "Main page",
                    _2fa: "Two-Factor Authentication",
                    payments: "Payments",
                    security: "Security",
                    how_it_works: "How it works",
                    pricing: "Pricing",
                    faq: "Help / FAQ",
                    files: "Files",
                    aes_keys: "AES keys",
                    login_history: "Login history",

                    notifs: "Notifications",
                    messages: "Messages",
                    messages_caption: "Manage your messages",

                    attachments: "Files",
                    attachments_caption: "Manage your files and recordings",

                    trigger: "Trigger",
                    trigger_caption: "Configure when Messages should be send out",

                    summary: "Summary",
                    summary_caption: "Review what the system is going to do",

                    account: "Account settings",
                    account_caption: "Account settings",

                    logout: "Log out",
                    login: "Log in",
                    create_account: "Sign up",
                    contact: "Contact",
                    tos: "Terms of Service",
                },
                msg: {
                    folder: {
                        outbox: "Outbox",
                        drafts: "Drafts",
                    },
                    decrypt: {
                        caption: "This message is encrypted, enter the password to open it",
                        success: "Message has been decrypted",
                        fail: "Message decryption failed: <b>{0}</b>",
                    },
                    validation: {
                        undefined_recipients: "empty recipients"
                    },
                    from: "From",
                    to: "To",
                    title: "Title",
                    no_title: "empty title",
                    creation_date: "Creation date",
                    modification_date: "Last modification date",
                    attachments: "Attachments",
                    edit: "Edit"
                },
                file: {
                    folder: {
                        file_misc: "Uploaded files",
                        vid_new: "New recordings",
                        file_encrypted: "Encrypted files",
                    },
                },
                btn: {
                    ok: "Ok",
                    cancel: "Cancel",
                    confirm: "Confirm",
                    close: "Close",
                    back: "Back",
                    save: "Save",
                    send: "Send",
                    move_to: "Move to",
                    encrypt: "Encrypt",
                    decrypt: "Decrypt",
                    open: "Open",
                    more: "More",
                    remove: "Remove",
                    change_passwd: "Change password",
                    preview: "Preview",
                    discard: "Discard",
                    attach_files: "Attach files",
                    record_video: "Record video",
                    done: "Done",
                },

                email_addr: "E-mail address",
                language: "Language",
                hint: "Hint",
                warning: "Warning",
                insert_aes_key: "Enter encryption key",

                fw: {
                    upload_time: "Upload time",
                    file_size: "File size",
                    recording_length: "Recording length"
                }

            },
        })

    });