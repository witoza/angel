angular
    .module('emi.resetPasswd', [])
    .controller('resetPasswdCtrl',
        function ($location, $routeParams, $scope, MyLoginService, MyUtils, $translator) {
            console.info('welcome resetPasswdCtrl');

            $translator.provide("resetPasswdCtrl", {
                pl: {
                    reset_password: {
                        caption: "Ustaw nowe hasło",
                        info1: "Użyj formularza poniżej aby ustawić nowe hasło",
                        welcome: "Witaj {0}",
                        set_password: "Ustaw hasło",
                        success_chnaged: "Hasło zostało zmienione. Możesz się teraz zalogować",
                        new_passwd: "Nowe hasło",
                        retype_new_passwd: "Powtórz nowe hasło"
                    },
                },
                en: {
                    reset_password: {
                        caption: "Set new password",
                        info1: "Use the form below to set a new password",
                        welcome: "Welcome {0}",
                        set_password: "Set password",
                        success_chnaged: "Password has been changed. You may now log-in",
                        new_passwd: "New password",
                        retype_new_passwd: "Retype new password"
                    },

                }
            });

            $scope.data = {
                username: $routeParams.username,
                passwd_new_1: "",
                passwd_new_2: ""
            };

            $scope.invoke = function () {
                console.log("resetPasswd invoke");

                const data = $scope.data;

                if (data.passwd_new_1 != data.passwd_new_2) {
                    MyUtils.show_alert("%passwords_not_the_same%");
                    return;
                }
                MyLoginService.change_passwd_by_reset_key({

                    secret: $routeParams.secret,
                    username: $routeParams.username,
                    reset_passwd_key: $routeParams.key,
                    passwd_new: data.passwd_new_1

                })
                    .then(function () {
                        MyUtils.show_info("%reset_password.success_chnaged%");
                        $location.url($location.path("/"));
                    });

            };

        });