angular
    .module('emi.login', [])
    .controller('loginCtrl',

        function ($q, $scope, $rootScope, MyLoginService, MyStart, MyUserService, MyUtils, MyDialog, $translator, $interval, $timeout) {
            console.info("welcome loginCtrl");

            $translator.provide("loginCtrl", {
                pl: {
                    user_accepts_tos: "Akceptuje regulamin",
                    user_not_accepts_tos: "Nie akceptuje regulaminu",
                    logout: {
                        success: "Zostałeś poprawnie wylogowany",
                    },
                    login: {
                        login_failed: "Nie udało się zalogować: <b>{0}</b>",
                        test_accounts: "Konta testowe",
                        forgot_password: "Nie pamiętam hasła",
                        forgot_token: "Nie pamiętam tokenu",
                        btn_reset_password: "Zresetuj hasło",

                        reset_passwd_requested: "Wysłaliśmy Ci email, jeśli znajduje się on w naszej bazie danych",
                        reset_passwd_reques_info: "Wpisz swój adres email i kliknij przycisk 'Zresetuj hasło'. <br/>Na podany adres zostanie wysłany link resetujący hasło.",

                        forgot_token_requested: "Email z detalami klucza OTP został wysłany na <b>{0}</b>",
                        forgot_token_request_info: "Detale klucza OTP zostaną wysłane na zdefiniowany adres email"
                    },
                },
                en: {
                    user_accepts_tos: "I accept Terms of Service",
                    user_not_accepts_tos: "I reject Terms of Service",
                    logout: {
                        success: "You have been successfully logged out",
                    },
                    login: {
                        login_failed: "Login failed: <b>{0}</b>",
                        test_accounts: "Test accounts",
                        forgot_password: "Forgot my password",
                        forgot_token: "Forgot security token",
                        btn_reset_password: "Reset password",

                        reset_passwd_requested: "We have sent you an email if we recognised the email you put in",
                        reset_passwd_reques_info: "Type your email address and click 'Reset password'. <br/>The reset password link will be send to the provided address.",

                        forgot_token_requested: "The email with token details was sent to <b>{0}</b>",
                        forgot_token_request_info: "OTP key details will be sent to the defined email address"
                    },
                }
            });

            $scope.showLoginDialog = function (ev) {
                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/login/login.login.dialog.html',
                }, $scope);
            };

            $scope.showRegisterUserDialog = function (ev) {
                MyDialog.showBasicDialog(ev, {
                    templateUrl: '/login/signUp.dialog.html',
                }, $scope);
            };

            $scope.forgotPasswdDialog = function ($event) {
                MyDialog.showBasicDialog($event, {
                    templateUrl: '/login/login.forgot_passwd.dialog.html',
                }, $scope);
            };

            $scope.forgotTokenDialog = function ($event) {

                MyDialog.showConfirm($event, $translator.translate("%login.forgot_token_request_info%"))
                    .then(function () {
                        return MyLoginService.recall_totp_secret($scope.signIn.data);
                    })
                    .then(function (data) {
                        MyUtils.show_info("%login.forgot_token_requested%", data.emailSentTo);
                    });

            };

            $scope.signIn = {
                show_totpToken: false,
                show_loginToken: false,

                data: {
                    username: "",
                    passwd: "",
                    loginToken: "",
                    totpToken: ""
                },

                invoke: function () {
                    console.log("signIn invoke");

                    let that = $scope.signIn;

                    let loginToken = null;
                    if (that.show_loginToken) {
                        loginToken = that.data.loginToken;
                    }

                    let totpToken = null;
                    if (that.show_totpToken) {
                        totpToken = that.data.totpToken;
                    }
                    return MyLoginService.login({

                        username: that.data.username,
                        passwd: that.data.passwd,
                        totpToken: totpToken,
                        loginToken: loginToken

                    })
                        .then(MyDialog.closeDialog)
                        .then(function () {

                            delete that.data;
                            delete $rootScope.user;

                            if ($rootScope.nagInstalled_timer) {
                                $timeout.cancel($rootScope.nagInstalled_timer);
                                delete $rootScope.nagInstalled_timer;
                            }

                            if ($rootScope.nagInstalled) {
                                $interval.cancel($rootScope.nagInstalled);
                                delete $rootScope.nagInstalled;
                            }

                            return MyStart.refreshUserData();
                        })
                        .then(function () {
                            if ($rootScope.user.tosAccepted) {
                                if ($rootScope.isAdmin()) {
                                    $rootScope.open_page("/admin");
                                } else {
                                    $rootScope.open_page("/summary");
                                }
                            }
                        })
                        .catch(function (err) {

                            let extracted = MyUtils.extract_error_mesage(err);

                            if (extracted === "Please provide security token") {
                                $scope.signIn.show_totpToken = true;
                                MyUtils.show_critical("<b>{0}</b>", err);
                            } else if (extracted === "Please provide login token") {
                                $scope.signIn.show_loginToken = true;
                                MyUtils.show_critical("<b>{0}</b>", err);
                            } else {
                                MyUtils.show_critical("%login.login_failed%", err);
                            }

                        });
                }
            };

            $scope.do_login = function (username, passwd) {
                $scope.signIn.data = {
                    username,
                    passwd,
                    totpToken: null
                };
                $scope.signIn.invoke();
            };

            $scope.do_logout = function () {
                console.log("do_logout");

                return MyUserService.logout()
                    .then(function () {

                        delete $rootScope.user;

                        MyUtils.show_info("%logout.success%");

                        $rootScope.open_page("/");
                    });
            };

            $scope.forgotPasswd = {
                username: "",
                invoke: function () {
                    console.log("forgotPasswd invoke");

                    let that = this;

                    return MyLoginService.reset_passwd({
                        username: that.username
                    })
                        .then(MyDialog.closeDialog)
                        .then(function () {
                            delete that.username;
                            MyUtils.show_info("%login.reset_passwd_requested%");
                        });

                }
            };

            $scope.tos = {
                loading: false
            };

            $scope.acceptTermsOfServices = function () {
                console.log("acceptTermsOfServices");
                $scope.tos.loading = true;

                const data = {
                    tosAccepted: true
                };
                return MyUserService.update_user(data)
                    .then(function (user) {
                        $rootScope.user = user;

                        return MyDialog.closeDialog().then(function () {
                            $scope.tos.loading = false;
                            $rootScope.open_page("/summary");
                        });

                    });

            };

            $scope.declineTermsOfServices = function () {
                console.log("declineTermsOfServices");
                $scope.tos.loading = true;

                const data = {
                    tosAccepted: false
                };
                return MyUserService.update_user(data)
                    .then(function () {
                        return MyDialog.closeDialog().then(function () {
                            $scope.tos.loading = false;
                            return $scope.do_logout();
                        });

                    });

            };

        }
    );