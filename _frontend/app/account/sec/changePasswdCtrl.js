angular
    .module('emi.sec.changePasswd', [])
    .controller('changePasswdCtrl',

        function ($q, $scope, MyUserService, MyStart, MyUtils, MyDialog, $translator, $rootScope) {

            console.info('welcome to changePasswdCtrl');

            $translator.provide("changePasswdCtrl", {
                pl: {
                    account: {
                        change_passwd: "Zmień hasło logowania",
                    },
                    password_change: {
                        info: "Zmień hasło logowania",
                        current_passwd: "Obecne hasło",
                        new_passwd: "Nowe hasło",
                        retype_new_passwd: "Powtórz nowe hasło",
                        success: "Twoje hasło zostało zmienione",
                        fail: "Nie udało się zmienić hasła: <b>{0}</b>",
                    },
                },
                en: {
                    account: {
                        change_passwd: "Change login password",
                    },
                    password_change: {
                        info: "Change login password",
                        current_passwd: "Current password",
                        new_passwd: "New password",
                        retype_new_passwd: "Repeat new password",
                        success: "Your password has been changed",
                        fail: "Error occurred during changing password: <b>{0}</b>",
                    },
                }
            });

            $scope.changePasswdDialog = function (ev) {
                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/account/sec/changePasswd.dialog.html',
                }, $scope);
            };

            function fixUserCopy() {
                $scope.user_copy = {
                    allowPasswordReset: $rootScope.user.allowPasswordReset,
                    verifyUnknownBrowsers: $rootScope.user.verifyUnknownBrowsers,
                };
            }

            fixUserCopy();

            $scope.updateUser = function () {
                return MyUserService.update_user($scope.user_copy)
                    .then(function () {
                        return MyStart.refreshUserData(true);
                    })
                    .then(function () {
                        MyUtils.show_info("%settings_has_been_updated%");
                        fixUserCopy();
                    });
            };

            $scope.changePasswd = {
                data: {},
                invoke: function () {
                    let data = $scope.changePasswd.data;

                    console.log("changePasswd", data);
                    if (data.passwd_new1 != data.passwd_new2) {
                        MyUtils.show_alert("%passwords_not_the_same%");
                        return;
                    }

                    MyUserService.change_passwd({
                        passwd: data.passwd,
                        passwd_new: data.passwd_new1
                    })
                        .then(MyDialog.closeDialog)
                        .then(function (res) {
                            $scope.changePasswd.data = {};
                            MyUtils.show_info("%password_change.success%");
                        }, function (err) {
                            MyUtils.show_critical("%password_change.fail%", err);
                            return $q.reject(err);
                        });

                }
            };

        }
    )