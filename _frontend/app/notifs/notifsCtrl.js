angular
    .module('emi.notifs', [])
    .controller('notifsCtrl',
        function ($rootScope, $scope, MyStart, MyNotifService, $translator) {

            console.info('welcome to notifsCtrl');

            $translator.provide("notifsCtrl", {
                pl: {
                    date: "Data",
                    message: "Wiadomość",
                    btn: {
                        mark_as_read: "Oznacz jako przeczytane",
                    },
                },
                en: {
                    date: "Date",
                    message: "Message",
                    btn: {
                        mark_as_read: "Mark as read",
                    },
                }
            });

            $scope.notifs = [];
            $scope.get_all = function () {
                console.log("get_all");
                return MyNotifService.all()
                    .then(function (data) {
                        $scope.notifs = data;
                    });
            };

            $scope.get_all();

            $scope.mark_as_read = function (notif) {
                console.log("mark_as_read");
                return MyNotifService.mark_as_read({
                    uuid: notif.uuid
                })
                    .then($scope.get_all)
                    .then(function () {
                        return MyStart.refreshUserData(true);
                    })
            };

        }
    );