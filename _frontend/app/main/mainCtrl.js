angular
    .module('emi.main', [])
    .controller('mainCtrl',

        function ($scope, $rootScope, $translator) {

            console.info('welcome mainCtrl');

            $scope.set_lang = function (lang) {
                $translator.set_lang(lang);
            };

            $scope.open_msgs = function () {
                $rootScope.open_page("/msgs");
                if ($rootScope.curr_scope != null) {
                    $rootScope.curr_scope.open_dir($rootScope.curr_scope.activeDir);
                }
            };

            $scope.open_files = function () {
                $rootScope.open_page("/files");
            };

            $scope.open_trigger = function () {
                $rootScope.open_page("/trigger");
            };

            $scope.open_summary = function () {
                $rootScope.open_page("/summary");
            };

            $scope.open_account = function () {
                $rootScope.open_page("/account");
            };

            $scope.open_notifications = function () {
                $rootScope.open_page("/notifs");
            };

            $scope.open_admin = function () {
                $rootScope.open_page("/admin");
            };

            $scope.open_contact = function () {
                $rootScope.open_page("/contact");
            };

            $scope.open_faq = function () {
                $rootScope.open_page("/faq");
            };

            $scope.open_tos = function () {
                $rootScope.open_page("/tos");
            };

            $scope.open_security = function () {
                $rootScope.open_page("/security");
            };

            $scope.open_main = function () {
                $rootScope.open_page("/");
            };

            $scope.open_how_it_works = function () {
                $rootScope.open_page("/#how_it_works");
            };

            $scope.open_pricing = function () {
                $rootScope.open_page("/#pricing");
            };

        }
    );