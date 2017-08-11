"use strict";

angular
    .module('emi.msgs', [])
    .controller('msgsCtrl',

        function ($window, $q, $scope, $rootScope, MyMessageService, MyFileService, MyUtils, MyDialog, $translator, $mdConstant) {

            var semicolon = 186;
            $scope.customKeys = [$mdConstant.KEY_CODE.ENTER, $mdConstant.KEY_CODE.COMMA, semicolon];

            $translator.provide("msgsCtrl", {
                pl: {
                    file_has_been_encrypted: "Plik <b>{0}</b> został zaszyfrowany",
                    file_has_been_decrypted: "Plik <b>{0}</b> został odszyfrowany",
                    no_msg: "Brak wiadomości",
                    msg: {
                        save: {
                            success: "Zapisano"
                        },
                        lang: {
                            title: "Ustaw język wiadomości",
                            btn: "Ustaw język",
                            info: "Ustaw język wyświetlania wiadomości oraz język powiadomień adresatów tej wiadomości",
                            set_default: "Ustaw domyślny",
                        },
                        drafts_warning: "Tylko wiadomości umieszczone w folderze <b>{0}</b> zostaną wysłane",
                        validation: {
                            invalid_email_addr: "Nieprawidłowy adres email: <b>{0}</b>",
                            encrypted: "Wiadomość zaszyfrowana hasłem",
                        },
                        new: {
                            btn: "Utwórz wiadomość",
                            create: {
                                success: "Wiadomość została utworzona"
                            }
                        },
                        new_from_attachment: "Nowa wiadomość z załącznikiem <b>{0}</b> została utworzona w folderze <b>{1}</b>",
                        has_been_encrypted: "Treść wiadomości została zaszyfrowana",
                        attachment_will_be_removed_from_msg: "Załącznik <b>{0}</b> zostanie usunięty z tej wiadomości.",
                        remove: {
                            success: "Wiadomość <b>{0}</b> została usunięta",
                            fail: "Nie udało się usunąć wiadomości: <b>{0}</b>"
                        },
                        encryption: {
                            caption: "Zaszyfruj wiadomość",
                            info: "Treść wiadomości jest zaszyfrowana hasłem",
                            password_need_to_be_known_info: "Hasło musi być znane odbiorcom do odszyfrowania wiadomość",
                            btn: {
                                remove_password: "Usuń hasło",
                                set_password: "Ustaw hasło"
                            },
                            password_remove: {
                                success: "Hasło zostało usunięte",
                                fail: "Nie udało się usunąć hasła: <b>{0}</b>",
                            },
                        }
                    }
                },
                en: {
                    file_has_been_encrypted: "File <b>{0}</b> has been encrypted",
                    file_has_been_decrypted: "File <b>{0}</b> has been decrypted",
                    no_msg: "No messages",
                    msg: {
                        save: {
                            success: "Saved"
                        },
                        lang: {
                            title: "Set message language",
                            btn: "Set language",
                            info: "Set message display language and the language of the recipients notifications of this message",
                            set_default: "Reset default",
                        },
                        drafts_warning: "Only messages in the folder <b>{0}</b> will be sent",
                        validation: {
                            invalid_email_addr: "Invalid email address: <b>{0}</b>",
                            encrypted: "Encrypted by password",
                        },
                        new: {
                            btn: "Create message",
                            create: {
                                success: "Message has been created"
                            }
                        },
                        new_from_attachment: "New message with attachment <b>{0}</b> has been created in the folder <b>{1}</b>",
                        has_been_encrypted: "Message content has been encrypted",
                        attachment_will_be_removed_from_msg: "Attachment <b>{0}</b> will be removed from this message.",
                        remove: {
                            success: "Message <b>{0}</b> has been removed",
                            fail: "Error occurred during removing message: <b>{0}</b>"
                        },
                        encryption: {
                            caption: "Encrypt the message",
                            info: "Message content is encrypted by password",
                            password_need_to_be_known_info: "The password must be known to the recipients to decrypt the message",
                            btn: {
                                remove_password: "Remove password",
                                set_password: "Set password"
                            },
                            password_remove: {
                                success: "Password has been removed",
                                fail: "Error occurred during removing password: <b>{0}</b>",
                            },
                        }
                    }
                }
            });

            $scope.open_set_lang = function (ev) {
                let modalScope = MyDialog.showBasicDialog(ev, {
                    templateUrl: '/msgs/msgs.set_language.dialog.html',
                }, $scope);

                modalScope.saveMessage = function () {
                    console.log("saveMessage");
                    $scope.update_msg().then(modalScope.closeDialog);
                };

                modalScope.setDefault = function () {
                    console.log("setDefault");
                    $scope.curr_msg.lang = null;
                    $scope.update_msg().then(modalScope.closeDialog);
                }
            };

            $scope.msgPanelScope = $scope;
            $rootScope.curr_scope = $scope;

            console.info('welcome to msgsCtrl');

            $scope.file_widget = {
                show_show_info: true,
                show_attachments: true,
                allow_endecrypt: true,
                show_thumb: true,

                edit_msg: true,
            };

            function update_gui_msg(msg) {
                let arr = $scope.msgs[msg.type];
                let index = arr.findIndex(function (m) {
                    return m.uuid === msg.uuid;
                });
                if (index === -1) {
                    throw new Error("can't find msg with uuid:" + msg.uuid);
                }
                arr[index] = msg;
                if (msg.type === 'outbox') {
                    validate_outbox_recipients();
                }
            }

            function set_curr_msg(msg) {
                console.log("set_curr_msg", msg);
                var cpy = MyUtils.clone(msg);
                MyUtils.augmentFiles(cpy.files);
                $scope.curr_msg = cpy;
                $scope.contentEditor.setData($scope.curr_msg.content);
            }

            function reload_current_message() {

                let passwd = $scope.curr_msg.encryption_passwd;
                let uuid = $scope.curr_msg.uuid;

                return MyMessageService.load_msg({
                    uuid
                }).then(function (data_msg) {
                    let msg = MyUtils.clone(data_msg);
                    update_gui_msg(msg);
                    return _decrypt(msg, passwd);
                }).then(function (msg) {
                    set_curr_msg(msg)
                });
            }

            $scope.encrypt_file = function (file) {
                console.log("encrypt_file", file);
                $scope.encrypt_file.loading = true;

                return MyFileService.encrypt({
                    msg_uuid: $scope.curr_msg.uuid,
                    file_uuid: file.uuid,
                    encryption_passwd: $scope.curr_msg.encryption_passwd
                })
                    .then(reload_current_message)
                    .then(function () {
                        MyUtils.show_info("%file_has_been_encrypted%", file.name);
                    })
                    .catch(function (err) {
                        console.error("encryption error", err);
                    })
                    .then(function () {
                        delete $scope.encrypt_file.loading;
                    });
            };

            $scope.decrypt_file = function (file) {
                console.log("decrypt_file", file);
                $scope.decrypt_file.loading = true;

                return MyFileService.decrypt({
                    msg_uuid: $scope.curr_msg.uuid,
                    file_uuid: file.uuid,
                    encryption_passwd: $scope.curr_msg.encryption_passwd
                })
                    .then(reload_current_message)
                    .then(function () {
                        MyUtils.show_info("%file_has_been_decrypted%", file.name);
                    })
                    .catch(function (err) {
                        console.error("decryption error", err);
                    })
                    .then(function () {
                        delete $scope.decrypt_file.loading;
                    });
            };

            $scope.remove_file_from_attachments = function (ev, sfile, force) {
                console.info('remove_file_from_attachments', sfile, force);

                let p = Promise.resolve();
                if (!force) {
                    p = MyDialog.showConfirm(ev, $translator.translate("%msg.attachment_will_be_removed_from_msg%", sfile.name));
                }
                return p.then(function () {
                        MyUtils.remove_arr($scope.curr_msg.attachments, function (attachment) {
                            return attachment === sfile.uuid;
                        });
                        return _update_message($scope.curr_msg);
                    }
                );
            };

            $scope.do_preview = function (msg) {
                console.log("do_preview");
                $window.open('/#!/preview?user_uuid=' + encodeURIComponent($rootScope.user.uuid) + "&msg_uuid=" + encodeURIComponent(msg.uuid) + "&releaseKey=", '_blank');
            };

            $scope.set_password = function (ev) {

                var msg = $scope.curr_msg;
                console.info('set_password', msg);

                var data = {
                    hint: msg.passwordEncryption && msg.passwordEncryption.hint,
                    passwd1: msg.encryption_passwd,
                    passwd2: msg.encryption_passwd,
                };

                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/msgs/msgs.encrypt_msg.dialog.html',
                }, $scope);

                $scope.setpassword = data;

                if (msg.has_been_decrypted) {
                    data.remove_password = function () {
                        console.info('remove password');
                        if (!msg.has_been_decrypted) {
                            throw new Error("button should not be available");
                        }
                        MyMessageService.remove_password({
                            uuid: msg.uuid,
                            encryption_passwd: msg.encryption_passwd
                        }).then(
                            function (data_msg) {
                                MyUtils.show_info("%msg.encryption.password_remove.success%");
                                var msg = MyUtils.clone(data_msg);
                                update_gui_msg(msg);
                                set_curr_msg(msg);
                            },
                            function (err) {
                                MyUtils.show_critical("%msg.encryption.password_remove.fail%", err);
                                return $q.reject(err);
                            }
                        ).then(MyDialog.closeDialog);
                    }
                }

                data.set_password = function () {
                    console.info('set password');

                    if (data.passwd1 != data.passwd2) {
                        MyUtils.show_alert("%passwords_not_the_same%");
                        return;
                    }
                    MyMessageService.set_password({
                        uuid: msg.uuid,
                        hint: data.hint,
                        encryption_passwd: msg.encryption_passwd,
                        encryption_passwd_new: data.passwd1
                    }).then(function (data_msg) {
                            MyUtils.show_info("%msg.has_been_encrypted%");
                            var msg = MyUtils.clone(data_msg);
                            update_gui_msg(msg);
                            set_curr_msg(msg);
                        }
                    ).then(MyDialog.closeDialog);
                }

            };

            function _decrypt(msg_o, encryption_passwd) {
                console.log("_decrypt");
                return MyMessageService.decrypt({
                    uuid: msg_o.uuid,
                    encryption_passwd
                }).then(function (data_msg) {
                    var msg = MyUtils.clone(msg_o);
                    msg.has_been_decrypted = true;
                    msg.encryption_passwd = encryption_passwd;
                    msg.content = data_msg.content;
                    msg.attachments = data_msg.attachments;
                    msg.files = data_msg.files;
                    return msg;
                });
            }

            $scope.decrypt_message = function () {
                console.info('decrypt_message');

                var passwd = $scope.curr_msg.typed_passwd;
                return _decrypt($scope.curr_msg, passwd).then(
                    set_curr_msg,
                    function (err) {
                        MyUtils.show_critical("%msg.decrypt.fail%", err);
                        return $q.reject(err);
                    });
            };

            function prepareContentEditor() {

                var whatSet;

                if ($scope.contentEditor) {
                    throw new Error("already initialized");
                }

                $scope.contentEditor = {
                    setData: function (text) {
                        console.info("ContentEditor not yet ready for setData", text);
                        whatSet = text;
                    },
                    getData: function () {
                        console.info("ContentEditor not yet ready for getData", whatSet);
                        return whatSet;
                    },
                    destroy: function () {
                        console.info("ContentEditor not yet ready for destroy");
                    }
                };

                const ckInstance = CKEDITOR.appendTo('editor1', {
                    language: $translator.get_lang(),
                    height: "20em",
                    // skin: 'bootstrapck,/_pc/ckeditor-skin-bootstrapck/',
                    toolbarGroups: [
                        {name: 'styles', groups: ['styles']},
                        {name: 'basicstyles', groups: ['basicstyles', 'cleanup']},
                        {name: 'paragraph', groups: ['list', 'indent', 'blocks', 'align', 'bidi', 'paragraph']},
                        {name: 'clipboard', groups: ['clipboard', 'undo']},
                        {name: 'editing', groups: ['find', 'selection', 'spellchecker', 'editing']},
                        {name: 'links', groups: ['links']},
                        {name: 'insert', groups: ['insert']},
                        {name: 'forms', groups: ['forms']},
                        {name: 'tools', groups: ['tools']},
                        {name: 'document', groups: ['mode', 'document', 'doctools']},
                        {name: 'others', groups: ['others']},
                        '/',
                        {name: 'colors', groups: ['colors']},
                        {name: 'about', groups: ['about']}
                    ],

                    removeButtons: 'Subscript,Superscript,About,PasteText,PasteFromWord,Scayt,Link,Unlink,Anchor,Image,Table,HorizontalRule,SpecialChar,Source,Strike',
                    removePlugins: 'elementspath',
                    toolbarLocation: 'bottom',
                });
                ckInstance.on("destroy", function (event) {
                    console.log("CK is destroy", event);
                });

                ckInstance.on("instanceReady", function (event) {
                    console.log("CK is ready");
                    if (whatSet != null) {
                        console.log("invoking setData");
                        ckInstance.setData(whatSet);
                        whatSet = undefined;
                    }
                    $scope.contentEditor = ckInstance;
                });

                $scope.$on('$destroy', function () {
                    $scope.contentEditor.destroy();
                    delete $scope.contentEditor;
                });

            }

            prepareContentEditor();

            function validate_outbox_recipients() {
                $scope.msgs.outbox.forEach(function (msg) {
                    msg.recipients_isEmpty = msg.recipients.length == 0;
                    msg.recipients_isValidAddresses = MyUtils.isValidAddresses(msg.recipients);
                });
            }

            function _filter_unused(msg) {
                var copy = MyUtils.clone(msg);
                delete copy.owner;
                delete copy.files;
                delete copy.updateTime;
                delete copy.creationTime;
                delete copy.has_been_decrypted;
                delete copy.recipients_isEmpty;
                delete copy.recipients_isValidAddresses;
                delete copy.enc;
                return copy;
            }

            function _update_message(omsg) {

                var msg = _filter_unused(omsg);

                console.log("_update_message", msg);
                return MyMessageService.update_msg(msg)
                    .then(function (data_msg) {
                        var msg = MyUtils.clone(data_msg);
                        update_gui_msg(msg);

                        if (omsg.encryption_passwd != null) {
                            return _decrypt(msg, omsg.encryption_passwd).then(set_curr_msg)
                        } else {
                            set_curr_msg(msg);
                        }

                    })
                    .then(function () {
                        MyUtils.show_info("%msg.save.success%");
                    })

            }

            function _add_message(msg) {

                msg = _filter_unused(msg);

                console.log("_add_message", msg);

                msg.content = $scope.contentEditor.getData();

                return MyMessageService.add_msg(msg)
                    .then(function (data_msg) {

                        set_curr_msg(data_msg);
                        $scope.composing = false;
                        $scope.editing = true;
                        $scope.activeDir = msg.type;

                        return load_msg_list();
                    });
            }

            $scope.updateAttachements = function (ufiles) {
                console.log("updateAttachements - msg");

                return MyDialog.closeDialog().then(function () {

                    let attachmentsTrue = [];
                    for (let folder in ufiles) {
                        ufiles[folder].forEach(function (file) {
                            if (file.attachmentSelected) {
                                attachmentsTrue.push(file.uuid);
                            }
                        });
                    }

                    if (MyUtils.strings_arr_eq(attachmentsTrue, $scope.curr_msg.attachments)) {
                        console.log("nothing to update");
                        return $q.defer();
                    } else {
                        $scope.curr_msg.attachments = attachmentsTrue;
                        if ($scope.curr_msg.uuid == null) {
                            $scope.curr_msg.type = $scope.activeDir;
                            return _add_message($scope.curr_msg);
                        } else {
                            return _update_message($scope.curr_msg);
                        }
                    }

                });

            };

            $scope.loadSelectAttachmentsDialog = function ($event) {
                return MyDialog.showBasicDialog($event, {
                    templateUrl: '/msgs/msgs.select_attachment.dialog.html',
                    fullscreen: true,
                }, $scope);
            };

            $scope.loadRecordVideoDialog = function ($event) {

                let $modalScope = MyDialog.showBasicDialog($event, {
                    templateUrl: '/files/video.recording.dialog.html',
                    clickOutsideToClose: false,
                    fullscreen: true,
                }, $scope);

                $modalScope.on_close = function (file) {
                    console.log("attaching file", file);
                    $scope.curr_msg.attachments.push(file.uuid);

                    if ($scope.curr_msg.uuid == null) {
                        $scope.curr_msg.type = $scope.activeDir;
                        return _add_message($scope.curr_msg);
                    } else {
                        return _update_message($scope.curr_msg);
                    }
                };

            };

            $scope.activeDir = "outbox";
            $scope.msgs = {
                outbox: [],
                drafts: [],
            };

            var reset_view = $scope.set_default_view = function () {
                $scope.curr_msg = null;
                $scope.composing = false;
                $scope.editing = false;
            };

            function load_msg_list() {
                console.log("load_msg_list");

                return MyMessageService.get_abstract().then(function (data) {
                    console.log("Got all msgs");

                    var msgs = {
                        outbox: [],
                        drafts: []
                    };
                    data.forEach(function (msg) {
                        if (msg.type === 'outbox') msgs.outbox.push(msg);
                        if (msg.type === 'drafts') msgs.drafts.push(msg);
                    });
                    $scope.msgs = msgs;

                    validate_outbox_recipients();
                });
            }

            function init() {
                return load_msg_list().then(reset_view);
            }

            $scope.update_msg = function () {
                console.log("update_msg");

                $scope.curr_msg.content = $scope.contentEditor.getData();

                return _update_message($scope.curr_msg);
            };

            $scope.save_new_to_folder = function (folder) {
                console.log("save_new_to_folder", folder);
                if (folder == null) {
                    folder = $scope.activeDir;
                }

                $scope.curr_msg.type = folder;
                $scope.curr_msg.content = $scope.contentEditor.getData();

                return MyMessageService.add_msg($scope.curr_msg)
                    .then(function () {
                        $scope.activeDir = folder;
                        MyUtils.show_info("%msg.new.create.success%");
                        return init();
                    });
            };

            $scope.delete_msg = function (ev) {
                console.log("delete_msg");

                let p = MyDialog.showConfirm(ev, "Message <b>" + $scope.curr_msg.title + "</b> will be removed.");
                p.then(function () {

                    return MyMessageService.delete_msg({
                        uuid: $scope.curr_msg.uuid,
                        encryption_passwd: $scope.curr_msg.encryption_passwd
                    }).then(function () {
                        MyUtils.show_info("%msg.remove.success%", $scope.curr_msg.title);
                        return init();
                    }, function (err) {
                        MyUtils.show_critical("%msg.remove.fail%", err);
                        return $q.reject(err);
                    });

                })

                return p;
            };

            $scope.move_to_folder = function (folder) {
                console.log("move_to_folder", folder);

                var msg = {
                    type: folder,
                    uuid: $scope.curr_msg.uuid,
                    encryption_passwd: $scope.curr_msg.encryption_passwd
                };

                return MyMessageService.update_msg(msg)
                    .then(function (data) {
                        $scope.activeDir = folder;
                        return init();
                    });
            };

            $scope.compose_new_email = function () {
                console.log("compose_new_email");

                $scope.composing = true;
                $scope.editing = false;

                set_curr_msg({
                    title: '',
                    recipients: [],
                    content: '<p>...</p><br/><strong>-- ' + $scope.user.screenName + '</strong>',
                    attachments: [],
                    files: []
                })
            };

            $scope.open_dir = function (dirName) {
                console.log("open_dir", dirName);
                $scope.activeDir = dirName;
                reset_view();
            };

            $scope.openEmail = function (email) {
                console.log("openEmail - msg", email.uuid);
                $scope.edit_email_by_uuid(email.uuid);
            };

            $scope.edit_email_by_uuid = function (uuid) {
                console.log("edit_email_by_uuid", uuid);

                for (let folder in $scope.msgs) {
                    let msg = $scope.msgs[folder].find(function (msg) {
                        return msg.uuid === uuid
                    });
                    if (msg != null) {
                        return $scope.edit_email(msg);
                    }
                }
                throw new Error("can't edit email with", uuid);
            };

            $scope.edit_email = function (msg) {
                console.log("edit_email", msg);
                if (!$scope.user.validAesKey) {
                    return $scope.do_preview(msg);
                } else {
                    return MyMessageService.load_msg({
                        uuid: msg.uuid
                    }).then(function (data_msg) {
                        var msg = MyUtils.clone(data_msg);
                        update_gui_msg(msg);
                        set_curr_msg(msg);

                        $scope.activeDir = msg.type;
                        $scope.composing = false;
                        $scope.editing = true;
                    });
                }

            };

            $scope.createMessageFrom = function (file) {
                console.log("createMessageFrom - msg panel", file);

                $scope.compose_new_email();
                $scope.curr_msg.attachments = [file.uuid];
                $scope.curr_msg.type = 'drafts';

                return _add_message($scope.curr_msg)
                    .then(function () {
                        MyUtils.show_info("%msg.new_from_attachment%", file.name, "%msg.folder.drafts%");
                    })
            };

            if ($rootScope.to_create != null) {
                let file = $rootScope.to_create;
                $rootScope.to_create = null;
                $scope.createMessageFrom(file);
            } else if ($rootScope.to_open != null) {
                let to_open = $rootScope.to_open;
                $rootScope.to_open = null;
                init().then(function () {
                    $scope.openEmail(to_open);
                });
            } else {
                init();
            }

        }
    );