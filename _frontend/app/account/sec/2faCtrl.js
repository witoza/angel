angular
    .module('emi.sec.2fa', [])
    .controller('2faCtrl',

        function ($q, $scope, $rootScope, MyUserService, MyStart, MyUtils, MyDialog, $translator) {

            console.info('welcome to 2faCtrl');

            $translator.provide("2faCtrl", {
                pl: {
                    _2fa: {
                        new_secret_will_be_generated: "Nowy klucz OTP zostanie wygenerowany.<br/>Będziesz musieć ponownie włączyć uwierzytelniania wielopoziomowe.",
                    },
                },
                en: {
                    _2fa: {
                        new_secret_will_be_generated: "New OTP key will be generated.<br/>You will have to enable Two-Factor Authentication again.",
                    },
                }
            });

            function fixUserCopy() {
                $scope.user_copy = {
                    totpRecoveryEmail: $rootScope.user.totpRecoveryEmail,
                };
            }

            fixUserCopy();

            $scope.ttt = 0;

            function refreshUserData() {
                $scope.ttt ++;
                return Promise.resolve()
                    .then(function () {
                        return MyStart.refreshUserData(true);
                    })
                    .then(function () {
                        fixUserCopy();
                    });
            }

            $scope.generate_totp_secret = function ($event) {
                console.log("generate_totp_secret");

                MyDialog.showConfirm($event, $translator.translate("%_2fa.new_secret_will_be_generated%"))
                    .then(function () {
                        return MyUserService.generate_totp_secret()
                    })
                    .then(refreshUserData);

            };

            $scope.enable2FA = function ($event) {
                $scope.enable2FAForm.token = "";

                MyDialog.showBasicDialog($event, {
                    templateUrl: '/account/sec/2fa.enable.dialog.html',
                }, $scope);

            };

            $scope.disable2FA = function ($event) {
                MyDialog.showConfirm($event, "Do you want to disable Two-factor authentication ?")
                    .then(function () {
                        return MyUserService.disable_2fa();
                    })
                    .then(refreshUserData);
            };

            $scope.enable2FAForm = {
                token: "",

                invoke: function () {
                    console.log("enable2FAForm::invoke()");
                    MyUserService
                        .enable_2fa({
                            totpToken: $scope.enable2FAForm.token
                        })
                        .then(MyDialog.closeDialog)
                        .then(function () {
                            MyUtils.show_info("2FA has been enabled");
                        })
                        .then(refreshUserData);
                }
            };

            $scope.updateUser = function ($event) {
                return MyUserService.update_user($scope.user_copy)
                    .then(function () {
                        MyUtils.show_info("%settings_has_been_updated%");
                    })
                    .then(refreshUserData);
            };

            $scope.otpDetails = function (otpuri) {

                let i = otpuri.indexOf("?");
                let be = otpuri.substring(0, i);
                let af = otpuri.substring(i + 1);

                let res = "";
                for (let entry of af.split("&")) {

                    let j = entry.indexOf("=");

                    let key = entry.substring(0, j);
                    let value = entry.substring(j + 1);

                    res = res + (key + " = " + value) + "\n";
                }
                return res;
            }

        }
    );