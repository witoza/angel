angular
    .module('emi.sec.encKey', [])
    .controller('encKeyCtrl',

        function ($q, $scope, MyUserService, MyStart, MyUtils, MyDialog, $translator) {

            console.info('welcome to encKeyCtrl');

            $translator.provide("encKeyCtrl", {
                pl: {
                    account: {

                        show_key: "Pokaż klucz",
                        hide_key: "Ukryj klucz",
                        remove_key: "Usuń klucz",

                        remove_key_info: `  W przypadku utraty klucza szyfrowania, odczytanie, modyfikacja treści
                                            wiadomości i załączników będzie niemożliwa. Adresaci wiadomości chcąc
                                            wyświetlić wiadomość przeznaczoną dla nich będą musieć wprowadzić poprawny klucz.`,
                        set_key: "Ustaw klucz",
                    },
                    aes_key_set: {
                        caption: "Ustaw klucz szyfrowania",
                        success: "Klucz szyfrowania został ustawiony",
                        fail: "Nie udało się ustawić klucza szyfrowania: <b>{0}</b>"
                    },
                },
                en: {
                    account: {

                        show_key: "Show key",
                        hide_key: "Hide key",
                        remove_key: "Remove key",
                        remove_key_info: `  If you lose your Encryption Key encryption of your messages and attachments won't be possible even by the administrator.
                                            Recipients wanting to display a message intended for them will have to enter the correct key.`,
                        set_key: "Set key",
                    },
                    aes_key_set: {
                        caption: "Set encryption key",
                        success: "Encryption key has been set",
                        fail: "Error occurred during encryption key: <b>{0}</b>"
                    },
                }
            });

            $scope.removeAesKeyDialog = function (ev) {
                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/account/sec/encKey.remove.dialog.html',
                }, $scope);
            };

            $scope.modifyAesKeyDialog = function (ev) {
                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/account/sec/encKey.modify.dialog.html',
                }, $scope);
            };

            $scope.show = false;

            function set_aes_key(aes_key, passwd) {
                console.log("set_aes_key", aes_key, passwd);
                return MyUserService.set_aes_key({
                    aes_key,
                    passwd
                })
                    .then(MyDialog.closeDialog)
                    .then(function () {
                        MyUtils.show_info("%aes_key_set.success%");
                        return MyStart.refreshUserData(true);
                    }, function (err) {
                        MyUtils.show_critical("%aes_key_set.fail%", err);
                        return $q.reject(err);
                    });
            }

            $scope.get_aes_key = function () {
                return MyUserService.get_aes_key()
                    .then(function (data) {
                        $scope.aes_key = data.aes_key;
                    });
            };

            $scope.data = {};

            $scope.remove = function () {
                const data = $scope.data;

                set_aes_key(null, data.passwd)
                    .then(function () {
                        $scope.aes_key = "";
                        $scope.data = {};
                    })
            };

            $scope.set_key = function () {
                const data = $scope.data;

                set_aes_key(data.aes_key, data.passwd)
                    .then(function () {
                        $scope.aes_key = data.aes_key;
                        $scope.data = {};
                    })
            };

        }
    );