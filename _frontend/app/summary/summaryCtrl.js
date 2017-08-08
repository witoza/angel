angular
    .module('emi.summary', [])
    .controller('summaryCtrl',

        function ($window, $scope, $rootScope, MyMessageService, MyUtils) {

            console.info('welcome summaryCtrl');

            $scope.createNewMessage = function () {
                console.log("createNewMessage");
                $rootScope.open_page("/msgs");
            };

            $scope.configureTrigger = function () {
                console.log("configureTrigger");
                $rootScope.open_page("/trigger");
            };

            $scope.do_preview = function (msg) {
                console.log("do_preview");
                $window.open('/#!/preview?user_uuid=' + encodeURIComponent($rootScope.user.uuid) + "&msg_uuid=" + encodeURIComponent(msg.uuid), '_blank');
            };

            $scope.do_edit = function (msg) {
                console.log("do_edit");
                $rootScope.to_open = msg;
                $rootScope.open_page("/msgs");
            };

            function validate_recipients(folder) {
                folder.forEach(function (msg) {
                    msg.recipients_isEmpty = msg.recipients.length == 0;
                    msg.recipients_isValidAddresses = MyUtils.isValidAddresses(msg.recipients);
                });
            }

            function load_msg_list() {
                console.log("load_msg_list");

                MyMessageService.get_abstract()
                    .then(function (data) {
                        console.log("Got all msgs");

                        let msgs = {
                            outbox: [],
                            drafts: []
                        };
                        data.forEach(function (msg) {
                            if (msg.type === 'outbox') msgs.outbox.push(msg);
                            if (msg.type === 'drafts') msgs.drafts.push(msg);
                        });
                        $scope.msgs = msgs;

                        validate_recipients($scope.msgs.outbox);

                    });

            }

            load_msg_list();

        }
    );