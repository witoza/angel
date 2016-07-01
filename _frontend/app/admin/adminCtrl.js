angular
    .module('emi.admin', [])
    .controller('adminCtrl',

        function ($q, $rootScope, $scope, MyAdminService, MyUserService, MyUtils, MyDialog) {

            console.info('welcome to adminCtrl');

            $scope.activeDir = 'to_resolve';

            $scope.open_dir = function (dir) {
                if (dir === "stats") {
                    load_stats();
                } else if (dir === "to_resolve") {
                    load_to_resolve();
                } else if (dir === "resolved") {
                    load_resolved();
                }

                $scope.activeDir = dir;
            };

            $scope.show_issue = function (ev, notif) {
                console.log("show_issue");
                const modalScope = MyDialog.showBasicDialog(ev, {
                    templateUrl: "/admin/issue.show.dialog.html"
                }, $scope);
                modalScope.notif = notif;
            };

            function load_resolved() {
                return MyAdminService.with_status({
                    status: "resolved"
                }).then(function (data) {
                    $scope.resolved = data;
                });
            }

            function load_to_resolve() {
                return MyAdminService.with_status({
                    status: "unresolved"
                }).then(function (data) {
                    $scope.to_resolve = data;
                });
            }

            function load_stats() {
                return MyAdminService.stats()
                    .then(function (data) {
                        $scope.stats = data;
                    })
                    .then(function () {
                        return MyAdminService.metrics();
                    })
                    .then(function (data) {
                        $scope.metrics = data;
                    });
            }

            load_to_resolve();

            $scope.handle_issue = function (ev, item) {
                console.log("mark_as_resolved", item);

                const modalScope = MyDialog.showBasicDialog(ev, {
                    templateUrl: "/admin/issue.resolve.dialog.html"
                }, $scope);

                modalScope.item = item;

                modalScope.getUserEncryptedEncryptionKey = function ($event) {
                    return MyAdminService.get_user_encrypted_encryption_key({
                        uuid: item.userUuid
                    }).then(function (data) {
                        MyDialog.showAlert($event,
                            "<code>" + data.data + "</code>");
                    });
                };

                modalScope.reject = function () {
                    return MyAdminService.issue_reject({
                        uuid: item.uuid,
                        input: modalScope.input
                    })
                        .then(function (resolution) {
                            MyUtils.show_alert("Issue has been resolved: <b>{0}</b>", resolution.msg);
                            return load_to_resolve();
                        })
                        .then(MyDialog.closeDialog)
                };
                modalScope.resolve = function () {
                    return MyAdminService.issue_resolve({
                        uuid: item.uuid,
                        input: modalScope.input,
                        userEncryptionKeyBase64: modalScope.userEncryptionKeyBase64
                    })
                        .then(function (resolution) {
                            MyUtils.show_info("Issue  has been resolved: <b>{0}</b>", resolution.msg);
                            return load_to_resolve();
                        })
                        .then(MyDialog.closeDialog);
                };

            };

        }
    );