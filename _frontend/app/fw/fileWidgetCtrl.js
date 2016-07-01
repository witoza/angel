"use strict";

angular
    .module('emi.fw', [])
    .controller('fileWidgetCtrl',
        function ($scope, $rootScope, $translator, MyFileService, MyUtils, MyDialog, $document) {
            console.info('welcome to fileWidgetCtrl');

            $translator.provide("fileWidgetCtrl", {
                pl: {
                    fw: {
                        attach: "Załącz",
                        delete: "Usuń plik",
                        delete_attachment: "Usuń załącznik",
                        download: "Ściągnij plik",
                        details: "Szczegóły pliku",
                        create_new_message: "Utwórz nową wiadomość z tym plikiem w załączniku",

                        attachment_in: "Załącznik w",
                        this_msg: "Ta wiadomość",
                        enc_used_this: "Plik jest <b>zaszyfrowany</b>, należy do",
                        enc_used_in: "Plik jest <b>zaszyfrowany</b> i należy do tej wiadomości",
                        enc_attached: "<b>Jest użyty</b> jako załącznik",
                        enc_not_attached: "<b>Nie jest użyty</b> jako załącznik",

                    },
                    file_remove: {
                        are_you_sure: "Czy napewno chcesz usunąć plik <b>{0}</b> ?",
                        file_is_attached_to: "Jest on dołączony do następujących wiadomości",
                        success: "Plik <b>{0}</b> został usunięty",
                    },
                },
                en: {
                    fw: {
                        attach: "Attach",
                        delete: "Remove file",
                        delete_attachment: "Remove attachment",
                        download: "Download file",
                        details: "File details",
                        create_new_message: "Create new message with that file as an attachment",

                        attachment_in: "Attachment in",
                        this_msg: "This message",
                        enc_used_this: "File is <b>encrypted</b>, belongs to this message",
                        enc_used_in: "File is <b>encrypted</b> belongs to the message",
                        enc_attached: "<b>Is used</b> as an attachment",
                        enc_not_attached: "<b>Is not used</b> as an attachment",
                    },
                    file_remove: {
                        are_you_sure: "Do you really want to remove file <b>{0}</b> ?",
                        file_is_attached_to: "It is attached to the following messages",
                        success: "File <b>{0}</b> has been removed",
                    },
                }
            });

            $scope.download_file = function (file) {
                console.log("download_file", file);
                let el = $document.find("form[name='d_" + file.uuid + "']");
                console.log("el.length", el.length);
                el[0].submit();
            };

            $scope.create_message_from = function (file) {
                console.log("create_message_from", file);
                $rootScope.to_create = file;
                $rootScope.open_page("/msgs");
            };

            $scope.remove_file = function (ev, file) {
                console.log("remove_file", file);

                var modalScope = MyDialog.showBasicDialog(ev, {
                    templateUrl: '/fw/fileWidget.remove_file.dialog.html',
                }, $rootScope);

                modalScope.file = file;
                modalScope.open_msg = function (msg) {
                    $scope.open_msg(msg);
                };
                modalScope.do_remove = function () {
                    return modalScope.closeDialog()
                        .then(function () {
                            return remove_file_impl(ev, file);
                        });
                };

            };

            function remove_file_impl(ev, file) {
                console.log("remove_file_impl", file);
                return MyFileService.delete_file({
                    uuid: file.uuid
                }).then(function () {
                    MyUtils.show_info("%file_remove.success%", file.name);
                    if ($scope.msgPanelScope != null) {
                        return $scope.msgPanelScope.remove_file_from_attachments(ev, file, true);
                    } else {
                        return $scope.reset_view();
                    }
                });
            }

            $scope.open_msg = function (msg) {
                console.log("open_msg", msg);

                MyDialog.closeDialog().then(function () {
                    if ($scope.curr_msg) {
                        $scope.msgPanelScope.openEmail(msg);
                    } else {
                        $rootScope.to_open = msg;
                        $rootScope.open_page("/msgs");
                    }
                });

            }

        }
    );