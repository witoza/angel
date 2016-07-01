angular
    .module('emi.loginHistory', [])
    .controller('loginHistoryCtrl',

        function ($scope, MyUserService, $translator) {

            console.info('welcome to loginHistory');

            $translator.provide("loginHistoryCtrl", {
                pl: {
                    login_history: {
                        login_date: "Data",
                        ip_addr: "Adres IP",
                        login_status: "Status logowania",
                    },
                },
                en: {
                    login_history: {
                        login_date: "Date",
                        ip_addr: "IP address",
                        login_status: "Login status",
                    },
                }
            });

            $scope.loginHistory = [];
            $scope.get_login_history = function () {
                console.log("get_login_history");
                return MyUserService.get_login_history()
                    .then(function (data) {
                        $scope.loginHistory = data;
                    });
            };

            $scope.get_login_history();

        }
    );