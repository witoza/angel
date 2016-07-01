angular
    .module('emi.payments', [])
    .controller('paymentsCtrl',

        function ($translator) {

            console.info('welcome to paymentsCtrl');

            $translator.provide("paymentsCtrl", {
                pl: {
                    payments: {
                        date: "Data",
                        amount: "Kwota",
                        details: "Szczegóły"
                    }
                },
                en: {
                    payments: {
                        date: "Date",
                        amount: "Amount",
                        details: "Details"
                    }
                }
            });

        }
    )
    .controller('doPaymentsCtrl',

        function ($rootScope, $scope, MyUserService, MyDialog, MyUtils) {

            console.info('welcome to doPaymentsCtrl');

            $scope.has_address = false;
            $scope.bitcoin_address = "N/A";

            $scope.open_contact = function () {
                MyDialog.closeDialog().then(function(){
                    $rootScope.open_page("/contact");
                });
            };

            MyUserService.get_bitcoin_address()
                .then(function (data) {
                    $scope.bitcoin_address = data.address;
                    $scope.has_address = true;
                })
                .catch(function (err) {
                    MyUtils.cancel_display_generic_error();
                    $scope.bitcoin_address = "Error while obtaining payment address";
                })

        }
    );