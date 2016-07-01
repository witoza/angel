angular
    .module('emi.account', [])
    .controller('accountCtrl',

        function ($q, $rootScope, $scope, MyUserService, MyStart, MyUtils, MyDialog, $translator) {

            console.info('welcome to accountCtrl');

            $translator.provide("accountCtrl", {
                pl: {
                    account: {
                        valid_until: "Ważne do",
                        extend_account: "Przedłuż konto",
                        general_settings: "Ustawienia ogólne",
                        creation_time: "Data utworzenia konta",
                        storage_space: "Przestrzeń dyskowa",
                        remove_account: "Usuń konto",
                        screen_name: "Wyświetlana nazwa",
                        login: "Login",
                    },
                    btn: {
                        save_settings: "Zapisz ustawienia"
                    },
                    remove_account: {
                        success: "Twoje kono zostało usunięte",
                        fail: "Nie udało się usunąć konta: <b>{0}</b>",
                    },
                    storage_request: {
                        success: "Zapytanie zostało wysłane do administratora",
                        send: "Wyślij zapytanie",
                        request_size_in_mb: "Ilość (w MB)",
                        info: "Aktualnie masz dostęp do <b>{0}</b> pamięci, z czego u użyciu jest <b>{1}</b>. Skorzystaj z formularza poniżej, aby uzyskać więcej miejsca.",
                    },
                },
                en: {
                    account: {
                        valid_until: "Valid until",
                        extend_account: "Extend account",
                        general_settings: "General settings",
                        creation_time: "Account creation date",
                        storage_space: "Storage space",
                        remove_account: "Remove account",
                        screen_name: "Screen name",
                        login: "Login",
                    },
                    btn: {
                        save_settings: "Save settings"
                    },
                    remove_account: {
                        success: "Your account has been removed",
                        fail: "Error occurred during removing account: <b>{0}</b>",
                    },
                    storage_request: {
                        success: "Request has been sent to the administrator",
                        send: "Request storage",
                        request_size_in_mb: "Amount (in MB)",
                        info: "You have access to <b>{0}</b> of storage space which <b>{1}</b> is in use. Use the form below to request more space.",
                    },
                }
            });

            $scope.showHelp1 = function ($event) {
                MyDialog.showAlert($event,
                    "When account is no longer valid you will receive information how to extend your account.");
            };

            $scope.showHelp2 = function ($event) {
                MyDialog.showAlert($event, "<b>Screen name</b> is used instead of your e-mail address when referring to you.<br/>It is used on both website and emails.");
            };

            $scope.activeDir = 'account';

            $scope.open_dir = function (dir) {
                $scope.activeDir = dir;
            };

            function fixUserCopy() {
                $scope.user_copy = {
                    tosAccepted: $rootScope.user.tosAccepted,
                    screenName: $rootScope.user.screenName,
                    lang: $rootScope.user.lang,
                };
            }

            fixUserCopy();

            $scope.removeAccountDialog = function (ev) {
                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/account/account.remove.dialog.html',
                }, $scope);
            };

            $scope.updateUser = function ($event) {
                return MyUserService.update_user($scope.user_copy)
                    .then(function () {
                        return MyStart.refreshUserData(true);
                    })
                    .then(function () {
                        MyUtils.show_info("%settings_has_been_updated%");
                        fixUserCopy();
                    });
            };

            $scope.deleteUser = {
                data: {
                    passwd: null
                },
                invoke: function () {
                    let data = $scope.deleteUser.data;
                    console.log("deleteUser", data);
                    MyUserService.delete_user({
                        passwd: data.passwd
                    })
                        .then(MyDialog.closeDialog)
                        .then(function () {
                            delete $rootScope.user;
                            $scope.deleteUser.data = {};
                            MyUtils.show_info("%remove_account.success%");
                            $rootScope.open_page("/");
                        }, function (err) {
                            MyUtils.show_critical("%remove_account.fail%", err);
                            return $q.reject(err);
                        });
                }
            };

            $scope.rfs = {
                data: {
                    number_of_mb: null
                },
                invoke: function () {
                    let data = $scope.rfs.data;

                    console.log("request for more storage", data);

                    MyUserService.request_for_storage({
                        number_of_mb: data.number_of_mb
                    }).then(function () {
                        MyUtils.show_info("%storage_request.success%");
                        $scope.rfs.data.number_of_mb = null;
                    })

                }
            }

        }
    );