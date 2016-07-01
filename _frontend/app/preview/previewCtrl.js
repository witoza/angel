angular
    .module('emi.preview', [])
    .controller('previewCtrl',

        function ($location, $window, $q, $sce, $scope, $rootScope, $routeParams, MyPreviewService, MyUserService, MyUtils, $translator, $timeout) {

            console.info('welcome previewCtrl');

            $translator.provide("previewCtrl", {
                pl: {
                    msg: {
                        message: "Wiadomość od",
                    },
                    no_attachments: "Brak załączników",
                    invalid_aes_key: "Nieprawidłowy klucz szyfrowania",
                    prev: {
                        internal_title: "Tak będzie wyglądać wiadomość wysłana adresatom",
                        msg_not_exist_or_not_allowed: "Wiadomość nie istnieje lub nie masz do niej dostępu: <b>{0}</b>",
                        insert_aes_key_why: "Aby wyświetlić treść wiadomość oraz załączniki musisz podać klucz szyfrowania nadawcy",
                        msg_open_info: "Ta wiadomość została pierwszy raz otwarta {0}, <b>zostanie ona usunięta</b> po 7 dniach od daty pierwszego otwarcia, t.j. w dniu <b>{1}</b>",
                    }
                },
                en: {
                    msg: {
                        message: "Message from",
                    },
                    no_attachments: "No attachments",
                    invalid_aes_key: "Invalid encryption key",
                    prev: {
                        internal_title: "This is how the message will look like to the recipients",
                        msg_not_exist_or_not_allowed: "Message does not exist or you do not have access to it: <b>{0}</b>",
                        insert_aes_key_why: "Enter sender's encryption key to display the message",
                        msg_open_info: "This message was first opened at {0}, <b>it will be deleted</b> after 7 days from the date of the first opening which is <b>{1}</b>",
                    }
                }
            });

            $scope.print_dialog = function(){
                $timeout(function() {
                    $window.print();
                })
            };
            $scope.file_widget = {
                show_show_info: false,
                show_thumb: false,
                show_attachments: false,
                allow_endecrypt: false,

                preview : true,
            };

            $scope.close_preview = function () {
                console.log("close_preview");
                $window.close();
            };

            const user_uuid = $location.search().user_uuid;
            const msg_uuid = $location.search().msg_uuid;
            const releaseKey = $location.search().releaseKey;
            const recipientKey = $location.search().recipientKey;

            //need to be on scope, as file form uses that to download file
            $scope.releaseKey = releaseKey;
            $scope.recipientKey = recipientKey;
            $scope.is_preview = true;
            $scope.user_uuid = user_uuid;

            if (MyUtils.isEmpty(user_uuid) || MyUtils.isEmpty(msg_uuid)) {
                MyUtils.show_critical("%prev.msg_not_exist_or_not_allowed%", "bad param");
            }

            function expand_files_info() {
                MyUtils.augmentFiles($scope.curr_msg.files);

                if ($scope.curr_msg.files != null) {
                    $scope.curr_msg.files.forEach(function (file) {
                        file.show_info = false;
                    });
                }
            }

            function _decrypt(msg, encryptionPassword, encryptionKey) {
                console.log("_decrypt");
                return MyPreviewService.decrypt({
                    user_uuid,
                    msg_uuid: msg.uuid,
                    encryptionPassword,
                    releaseKey,
                    recipientKey,
                    encryptionKey,
                }).then(function (data_msg) {
                    msg.has_been_decrypted = true;
                    msg.content = data_msg.content;
                    msg.files = data_msg.files;
                });
            }

            $scope.decrypt_message = function () {
                console.info('decrypt_message');

                const encryption_passwd = $scope.curr_msg.encryption_passwd;

                return _decrypt($scope.curr_msg, encryption_passwd, $scope.aes_key)
                    .then(
                        function () {
                            MyUtils.show_info("%msg.decrypt.success%");
                            expand_files_info();
                        },
                        function (err) {
                            MyUtils.show_critical("%msg.decrypt.fail%", err);
                            return $q.reject(err);
                        });
            };

            $scope.aes_key = "";

            $scope.open_message = function () {
                console.info('open_message');

                return MyPreviewService.get_by_uuid({
                    user_uuid,
                    msg_uuid,
                    releaseKey,
                    recipientKey,
                    encryptionKey: $scope.aes_key,
                }).then(
                    function (data) {
                        $scope.curr_msg = data.msg;
                        $scope.curr_msg.invalid_aes_key = data.invalid_aes_key;
                        $scope.curr_msg.from = data.from;
                        $scope.curr_msg.recipients = [$scope.curr_msg.releaseItem.recipient];
                        $translator.set_lang(data.lang);

                        if ($scope.curr_msg.invalid_aes_key && !MyUtils.isEmpty($scope.aes_key)) {
                            MyUtils.show_critical("%invalid_aes_key%");
                        } else {
                            expand_files_info();
                        }
                    },
                    function (err) {
                        MyUtils.show_critical("%prev.msg_not_exist_or_not_allowed%", err);
                        return $q.reject(err);
                    });
            };

            return $scope.open_message();

        }
    );