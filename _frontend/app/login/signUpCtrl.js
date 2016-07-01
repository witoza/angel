angular
    .module('emi.signUp', [])
    .controller('signUpCtrl',
        function ($rootScope, $scope, MyLoginService, MyUtils, MyDialog, $translator) {
            console.info('welcome signUpCtrl');

            $translator.provide("signUpCtrl", {
                pl: {
                    register_new_user: {
                        key: "Kod aktywacyjny",

                        caption: "Rejestracja nowego użytkownika",
                        next: "Kontynuuj",
                        register: "Zarejestruj się",
                        success: "Konto użytkownika zostało utworzone.",

                    }
                },
                en: {
                    register_new_user: {
                        key: "Activation code",

                        caption: "Register new user",
                        next: "Next",
                        register: "Register new user",
                        success: "User's account has been created.",
                    }
                }
            });

            $scope.stage = 1;
            $scope.data = {};

            $scope.invokePreregister = function () {
                console.log("invokePreregister invoke");

                const data = {
                    username: $scope.data.username,
                    lang: $translator.get_lang(),
                };

                MyLoginService.preregister(data).then(
                    function () {
                        $scope.stage = 2;
                    });
            };

            $scope.invokeRegister = function () {
                console.log("invokeRegister invoke");

                if ($scope.data.passwd1 != $scope.data.passwd2) {
                    MyUtils.show_alert("%passwords_not_the_same%");
                    return;
                }

                const data = {
                    shortTimeKey: $scope.data.shortTimeKey,
                    passwd: $scope.data.passwd1,
                    lang: $translator.get_lang(),
                };

                MyLoginService.register(data)
                    .then(MyDialog.closeDialog)
                    .then(function () {
                        MyUtils.show_info("%register_new_user.success%", data.username);
                        $scope.data = {};
                        $rootScope.open_page("/summary");
                    });
            }


        });