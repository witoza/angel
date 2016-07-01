"use strict";

angular
    .module('emi.files', [])
    .controller('attaCtrl',

        function ($timeout, $sce, $q, $rootScope, $scope, Upload, MyFileService, MyUtils, MyDialog, $translator) {

            console.info('welcome to attaCtrl');

            $scope.attaPanelScope = $scope;

            $translator.provide("attaCtrl", {
                pl: {
                    file_upload: {
                        uploading: "Trwa wysylanie pliku <b>{0}</b>, rozmiar {1}",
                        success: "Plik <b>{0}</b> został wysłany",
                        canceled: "Wysyłanie pliku <b>{0}</b> zostało przerwane",
                        error: "Wystąpił błąd podczas wysyłania pliku: <b>{0}</b>",
                    },

                    no_files: "Brak przesłanych plików",

                    btn: {
                        upload_files: "Prześlij pliki",
                        upload_and_attach: "Prześlij i załącz",
                        upload: "Prześlij",
                    }

                },
                en: {
                    file_upload: {
                        uploading: "Uploading file <b>{0}</b>, file size {1}",
                        success: "File <b>{0}</b> has been uploaded",
                        canceled: "File <b>{0}</b> uploading has been cancelled",
                        error: "Error occurred during file uploading: <b>{0}</b>",
                    },

                    no_files: "No files",

                    btn: {
                        upload_files: "Upload files",
                        upload_and_attach: "Upload and attach",
                        upload: "Upload",
                    }

                }
            });

            console.info('curr_msg', $scope.curr_msg);

            var reset_view = $scope.reset_view = function () {
                console.log("reset_view");
                return load_file_list();
            };

            if ($scope.curr_msg == null) {
            } else {
                $scope.msgPanelScope.attachmentsScope = $scope;
            }

            $scope.file_widget = {
                show_show_info: true,
                show_attachments: true,
                show_thumb: true,
            };

            $scope.uploads = [];

            $scope.do_upload = function (files) {
                console.log("do_upload:", files, "files");

                let p = Promise.resolve();

                for (let file of files) {
                    const upload = new MyUtils.StandardUploader(function () {
                        console.log("do_upload", file);
                        this.file = file;
                        this.beforeUpload();

                        var uploader = this.uploader = Upload.upload({
                            url: '/api/file/upload',
                            data: {file: file}
                        });

                        var that = this;

                        return uploader
                            .then(function (data) {
                                    that.afterSuccess(data);
                                    that.file = null;
                                },
                                function (err) {
                                    that.afterError(err);
                                    that.file = null;
                                    return $q.reject(err);
                                },
                                this.defaultOnProgress.bind(that))
                            .then(function () {
                                return load_file_list();
                            });

                    });
                    $scope.uploads.push(upload);

                    p = p
                        .catch(function () {
                        })
                        .then(function () {
                            return upload.do_upload();
                        });
                }

            };

            $scope.activeDir = "file_misc";
            $scope.files = {
                file_misc: [],
                file_encrypted: []
            };

            function load_file_list() {

                console.log("load_file_list");

                return MyFileService.get_files()
                    .then(function (list_of_files) {

                        const files = {};
                        files.file_misc = list_of_files;

                        function process_files(files) {
                            MyUtils.augmentFiles(files);
                            files.forEach(function (file) {
                                if ($scope.curr_msg == null) {
                                    file.attachmentSelected = false;
                                } else {
                                    file.attachmentSelected = $scope.curr_msg.attachments.includes(file.uuid);
                                }
                            });
                        }

                        function is_encrypted(file) {
                            return file.passwordEncrypted;
                        }

                        process_files(files.file_misc);

                        files.file_encrypted = files.file_misc.filter(is_encrypted);
                        MyUtils.remove_arr(files.file_misc, is_encrypted);

                        if ($scope.curr_msg != null) {
                            MyUtils.remove_arr(files.file_encrypted, function (file) {
                                return file.belongsTo.uuid !== $scope.curr_msg.uuid;
                            });
                        }

                        files.file_encrypted.forEach(function (file) {
                            file.icons_expanded = true;
                        });

                        $scope.files = files;

                    });

            }

            $scope.click_start_recording = function () {
                console.log("click_start_recording");

                let $modalScope = MyDialog.showBasicDialog(null, {
                    templateUrl: '/files/video.recording.dialog.html',
                    clickOutsideToClose: false,
                    fullscreen: true,
                }, $scope);

                $modalScope.on_close = function (file) {
                    console.log("back to /files");
                    return load_file_list();
                };
            };

            $scope.open_dir = function (dirName) {
                console.log("open_dir", dirName);
                if ($scope.activeDir === dirName) {
                    return;
                }
                $scope.activeDir = dirName;
            };

            load_file_list();

        }
    );