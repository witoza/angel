angular
    .module('emi.contact', [])
    .controller('contactCtrl', function ($scope, $rootScope, MyLoginService, MyUtils, $translator, vcRecaptchaService, MyDialog) {
        console.info('welcome contactCtrl');

        $translator.provide("contactCtrl", {
            pl: {
                contact: {
                    info: "Uwagi, komentarze, zapytania prosze kierować na adres <a href='mailto:contact@postscriptum.co'><b>contact@postscriptum.co</b></a>, lub użyć formularza poniżej:</p>",
                    msg_has_been_sent: "Wiadomość została wysłana",
                    msg_title: "Temat",
                    msg_content: "Treść",
                },
            },
            en: {
                contact: {
                    info: "Please direct inquiries to <a href='mailto:contact@postscriptum.co'><b>contact@postscriptum.co</b></a> or use the form below",
                    msg_has_been_sent: "Message has been sent",
                    msg_title: "Title",
                    msg_content: "Content",
                },
            }
        });

        $scope.data = {};

        $scope.reset = function () {
            $scope.data = {
                title: "",
                from: "",
                content: "",
                myRecaptchaResponse: null,
            };
            if ($rootScope.user != null) {
                $scope.data.from = $rootScope.user.username;
            }
        };

        let widgetId;

        $scope.onWidgetCreate = function (_widgetId) {
            console.log("onWidgetCreate", _widgetId);
            widgetId = _widgetId;
        };

        $scope.invoke = function () {

            const data = $scope.data;
            console.log("send_message", data);

            if (MyUtils.isEmpty(data.from) || MyUtils.isEmpty(data.title) || MyUtils.isEmpty(data.content)) {
                throw new Error("bad data");
            }

            MyLoginService.send_message(data)
                .then(function () {
                        $scope.reset();
                        $scope.contactForm.$setPristine();
                        $scope.contactForm.$setUntouched();
                        vcRecaptchaService.reload(widgetId);

                        MyDialog.showAlert(null, $translator.translate("%contact.msg_has_been_sent%"));
                    }
                )
        };

        $scope.reset();
    });