"use strict";

angular
    .module('emi.utils', [])
    .factory('MyUtils', function ($mdToast, $translator, $rootScope) {

        let OBJ = {};

        class StandardUploader {
            constructor(do_upload) {
                this.is_uploading = false;
                this.uploaded = false;
                this.upload_progress = 0;
                this.file = null;
                this.uploader = null;

                this.do_upload = do_upload;
            }

            cancel() {
                console.log("cancelling upload");
                this.uploader.abort();
            }

            beforeUpload() {
                $rootScope.loading++;

                this.is_uploading = true;
                this.upload_progress = 0;
                this.uploaded = false;
            }

            afterSuccess(resp) {
                $rootScope.loading--;

                console.log('upload success', resp);
                this.is_uploading = false;
                this.uploaded = true;
                this.uploader = null;

                let key;
                if (OBJ.isVideo(resp.data)) {
                    key = "%rec_videos.upload_success%"
                } else {
                    key = "%file_upload.success%"
                }
                OBJ.show_info(key, resp.data.name, "%file.folder." + resp.data.type + "%");
            }

            afterError(err) {
                $rootScope.loading--;

                console.warn('upload error', err);
                this.is_uploading = false;
                this.uploaded = false;
                this.uploader = null;

                if (err.status === -1) {
                    let extra = [];
                    if (this.file != null) {
                        extra.push(this.file.name);
                    }
                    OBJ.show_critical("%file_upload.canceled%", ...extra);
                } else {
                    OBJ.show_critical("%file_upload.error%", err);
                }
            }

            defaultOnProgress(evt) {
                var upload_progress = parseInt(100.0 * evt.loaded / evt.total);
                console.log('upload progress', upload_progress, evt);
                this.upload_progress = upload_progress;
            }

        }

        OBJ.StandardUploader = StandardUploader;

        OBJ.title = function (title) {
            if (OBJ.isEmpty(title)) {
                return "<" + $translator.translate("%msg.no_title%") + ">";
            }
            return title;
        };

        OBJ.recipients = function (recipients) {
            if (recipients == null || recipients.length === 0) {
                return "<" + $translator.translate("%msg.validation.undefined_recipients%") + ">";
            }
            return recipients.join(", ");
        };

        OBJ.isEmpty = function (val) {
            if (val == null) {
                return true;
            }
            const type = typeof val;
            if (type === "number") {
                return false;
            } else if (type === "string") {
                return val.trim().length === 0;
            } else if (type === "object") {
                return false;
            }
            throw new Error("unknown object type:" + type);
        };

        function get_valid_emails(emails_str) {
            const emails = emails_str.split(/,|;/);
            return get_valid_emails_arr(emails);
        }

        OBJ.get_valid_emails = get_valid_emails;

        function get_valid_emails_arr(emails) {
            return emails
                .map(function (recipient) {
                    return recipient.trim();
                })
                .filter(function (recipient) {
                    return _validateEmail(recipient);
                });
        }

        OBJ.get_valid_emails_arr = get_valid_emails_arr;

        function _validateEmail(email) {
            var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
            return re.test(email);
        }

        OBJ._validateEmail = _validateEmail;

        OBJ.isValidAddresses = function (emails) {
            emails = emails.map(function (email) {
                return email.trim();
            });
            var invalid = emails.filter(function (email) {
                return !_validateEmail(email);
            });
            return invalid.join();
        };

        function extract_error_mesage(err) {
            if (OBJ.isEmpty(err)) {
                return "N/A"
            }
            let err_msg = (function () {
                if (err.status === -1) {
                    return "can't connect to the backend";
                }
                if (err.data != null) {
                    if (err.data.error != null) {
                        return err.data.error;
                    }
                    if (err.data.message != null) {
                        return err.data.message;
                    }
                    return err.data;
                }
                return err
            })();
            if (OBJ.isEmpty(err_msg)) {
                return "N/A"
            }
            return err_msg;
        }

        OBJ.extract_error_mesage = extract_error_mesage;

        var qd = null;

        function show_toast(data) {
            clearTimeout(qd);

            let message = $translator.translate(data.message, ...data.extra);

            return $mdToast.show({
                hideDelay: data.hideDelay,
                position: 'bottom left',
                template: `
<md-toast>
    <span class="md-toast-text" flex>
        <p class="md-body-1">${data.icon}&nbsp;&nbsp;${message}</p>
    </span>
</md-toast>`
            });

        }

        OBJ.show_info = function (message) {
            const extra = [].slice.call(arguments, 1);
            return show_toast({
                hideDelay: 3000,
                icon: '<md-icon style="color: green">done</md-icon>',
                message,
                extra
            });
        };

        OBJ.show_critical = function (message, err) {
            return show_toast({
                hideDelay: 5000,
                icon: '<md-icon style="color: red">clear</md-icon>',
                message,
                extra: [
                    extract_error_mesage(err)
                ]
            });
        };

        OBJ.show_alert = function (message, err) {
            return show_toast({
                hideDelay: 5000,
                icon: '<md-icon style="color: darkorange">clear</md-icon>',
                message,
                extra: [
                    extract_error_mesage(err)
                ]
            });
        };

        OBJ.cancel_display_generic_error = function () {
            clearTimeout(qd)
        };

        OBJ.show_critical_with_delay = function (message, err) {
            qd = setTimeout(function () {
                OBJ.show_critical(message, err);
            }, 200);

        };

        OBJ.guid = function () {
            function s4() {
                return Math.floor((1 + Math.random()) * 0x10000)
                    .toString(16)
                    .substring(1);
            }

            return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
                s4() + '-' + s4() + s4() + s4();
        };

        OBJ.remove_arr = function (arr, predicate, beforeDelete) {
            let total = 0;
            for (let i = 0; i < arr.length; i++) {
                if (predicate(arr[i])) {
                    if (beforeDelete != null) {
                        beforeDelete(arr[i]);
                    }
                    arr.splice(i, 1);
                    i--;
                    total++;
                }
            }
            return total;
        };

        OBJ.augmentFiles = function (files) {
            console.log("augmenting", files);
            if (files == null) {
                return;
            }
            if (files.augmented === true) {
                return;
            }
            files.augmented = true;
            files.forEach(function (file) {

                file.url = "/api/preview/download";
                file.open_url = "/api/file/open?file_uuid=" + file.uuid + "." + file.ext;

                if (OBJ.isVideo(file)) {
                    file.vjs_media = {
                        sources: [
                            {
                                src: file.open_url,
                                type: file.mime
                            }
                        ]
                    }
                }
            })
        };

        OBJ.getIcon = function (file) {
            let mime = file.mime;

            if (mime === "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
                return "fa-file-word-o";
            } else if (mime === "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") {
                return "fa-file-excel-o";
            } else if (mime === "application/pdf") {
                return "fa-file-pdf-o";
            } else if (mime === "text/plain") {
                return "fa-file-text";
            } else if (mime === "text/html" || mime === "text/xml") {
                return "fa-file-code-o";
            }
            return "fa-file";
        };

        OBJ.isVideo = function (file) {
            return file.mime.match("video/*") != null;
        };

        OBJ.isImage = function (file) {
            return file.mime.match("image/*") != null;
        };

        OBJ.clone = function (obj) {
            return JSON.parse(JSON.stringify(obj));
        };

        OBJ.strings_arr_eq = function (arr1, arr2) {
            let v1 = OBJ.clone(arr1).sort().join();
            let v2 = OBJ.clone(arr2).sort().join();
            return v1 === v2;
        };

        return OBJ;

    });
