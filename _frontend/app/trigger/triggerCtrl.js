angular
    .module('emi.trigger', [])
    .controller('triggerCtrl',

        function ($rootScope, $scope, MyUserService, MyStart, MyUtils, MyDialog, $translator) {

            console.info('welcome to triggerCtrl');

            $translator.provide("triggerCtrl", {
                pl: {
                    trigger: {
                        reminder_info1: "Wyślij przypomnienie tylko na mój adres email",
                        reminder_info2: "Wyślij przypomnienie na adresy emaili które zostały zdefiniowane jako odbiorcy dla tego etapu",
                        reminder_example_sent: "Przykładowa wiadomość weryfikacyjna została wysłana do <b>{0}</b>.",
                    },
                    trigger_update: {
                        success: "Zapisano"
                    }
                },
                en: {
                    trigger: {
                        reminder_info1: "Send notification to my email address only",
                        reminder_info2: "Send notification to all emails defined for this phase",
                        reminder_example_sent: "Sample email has been sent to <b>{0}</b>.",
                    },
                    trigger_update: {
                        success: "Saved"
                    }
                }
            });

            $scope.showHelp1 = function ($event) {

                let resetKey = $scope.user_copy.triggerInternal.resetKey;
                let user_uuid = $rootScope.user.uuid;
                let url = "https://postscriptum.co/api/alive?user_uuid=" + user_uuid + "&key=" + resetKey;
                let wgetUrl = "wget -qO- \"" + url + "\" > /dev/null";

                MyDialog.showAlert($event,
                    "Under Linux you may add following line to your cron to automate that step:<br/><br/>" +
                    "<code>" + wgetUrl + "</code>");
            };

            function fixUserCopy() {
                $scope.user_copy = {
                    trigger: MyUtils.clone($rootScope.user.trigger),
                    triggerInternal: MyUtils.clone($rootScope.user.triggerInternal)
                };
            }

            fixUserCopy();

            $scope.invokeTrigger = function () {
                console.log("invokeTrigger sendEmailOnlyToUser=", $scope.sendEmailOnlyToUser);

                MyDialog.closeDialog().then(function () {

                    return $scope.func.call(MyUserService, {
                        sendEmailOnlyToUser: $scope.sendEmailOnlyToUser
                    }).then(function (recipients) {
                        MyUtils.show_info("%trigger.reminder_example_sent%", recipients.join(", "))
                    });

                });

            };

            $scope.send_x_notification = function ($event) {
                console.log("send_x_notification");

                let $modalScope = MyDialog.showBasicDialog($event, {
                    templateUrl: '/trigger/invoke.trigger.dialog.html',
                }, $scope);

                $modalScope.func = MyUserService.send_x_notification;
                let triggerInternal = $scope.user_copy.triggerInternal;
                $modalScope.emails = MyUtils.get_valid_emails(triggerInternal.xemails);
                $modalScope.sendEmailOnlyToUser = "true";
            };

            $scope.send_y_notification = function ($event) {
                console.log("send_y_notification");

                let $modalScope = MyDialog.showBasicDialog($event, {
                    templateUrl: '/trigger/invoke.trigger.dialog.html',
                }, $scope);

                $modalScope.func = MyUserService.send_y_notification;
                let triggerInternal = $scope.user_copy.triggerInternal;
                $modalScope.emails = MyUtils.get_valid_emails(triggerInternal.xemails + ";" + triggerInternal.yemails);
                $modalScope.sendEmailOnlyToUser = "true";
            };

            $scope.send_z_notification = function ($event) {
                console.log("send_z_notification");

                let $modalScope = MyDialog.showBasicDialog($event, {
                    templateUrl: '/trigger/invoke.trigger.dialog.html',
                }, $scope);

                $modalScope.func = MyUserService.send_z_notification;
                let triggerInternal = $scope.user_copy.triggerInternal;
                $modalScope.emails = MyUtils.get_valid_emails(triggerInternal.xemails + ";" + triggerInternal.yemails + ";" + triggerInternal.zemails);
                $modalScope.sendEmailOnlyToUser = "true";
            };

            $scope.send_release_notification_dialog = function ($event) {
                console.log("send_release_notification_to_owner");

                MyDialog.showBasicDialog($event, {
                    templateUrl: '/trigger/invoke.release.dialog.html',
                }, $scope);
            };

            $scope.send_release_notification = function ($event) {
                console.log("send_release_notification");

                MyDialog.closeDialog().then(function () {
                    return MyUserService.send_release_notification_to_owner()
                        .then(function () {
                            MyUtils.show_info("Notifications have been sent")
                        });
                });

            };

            $scope.updateUser = function () {
                return MyUserService.update_user($scope.user_copy)
                    .then(function () {
                        MyUtils.show_info("%trigger_update.success%");
                        return MyStart.refreshUserData(true);
                    })
                    .then(function () {
                        fixUserCopy();
                    })
            }

        }
    )
;