"use strict";

angular
    .module('emi.recording', [])
    .controller('videoRecordingCtrl',

        function ($timeout, $sce, $q, $rootScope, $scope, Upload, MyFileService, MyUtils, MyDialog, $translator, $filter) {

            console.info('welcome to videoRecordingCtrl');

            $translator.provide("videoRecordingCtrl", {
                pl: {
                    rec_videos: {
                        video_recording: "Nagrywanie wideo",
                        new_title: "Nagranie z",
                        no_recordings: "Brak nowych nagrań",
                        uploading: "Trwa wysylanie nagrania",
                        upload_success: "Nagranie <b>{0}</b> zostało wysłane",
                    },

                    camera: {
                        error: "Problem z kamerą: <b>{0}</b>",
                        turnon_info: "Włącz obsługe kamery",
                        recording_error: "Problem occurred during recording, the video will be discarded. Please record the video again",
                    },

                    btn: {
                        camera: {
                            record: "Zacznij nagrywać",
                            resume: "Kontynuuj nagrywanie",
                            pause: "Pauza",
                        },
                        upload_and_attach: "Prześlij i załącz",
                        upload: "Prześlij",
                    }

                },
                en: {

                    rec_videos: {
                        video_recording: "Video recording",
                        new_title: "Recording from",
                        no_recordings: "No new recordings",
                        uploading: "Uploading recording",
                        upload_success: "Recording <b>{0}</b> has been uploaded",
                    },

                    camera: {
                        error: "Camera error: <b>{0}</b>",
                        turnon_info: "Turn on the camera",
                        recording_error: "Error occurred during recording, recording will be discarded. Please record again",
                    },

                    btn: {
                        camera: {
                            record: "Start recording",
                            resume: "Resume recording",
                            pause: "Pause",
                        },
                        upload_and_attach: "Upload and attach",
                        upload: "Upload",
                    }

                }
            });

            console.info('curr_msg', $scope.curr_msg);

            var recordRTC;

            function clear_RecordRTC() {
                if (recordRTC == null) {
                    return;
                }
                console.log("clear_RecordRTC");

                recordRTC.clearRecordedData();

                if (recordRTC.stream != null) {
                    recordRTC.stream.getAudioTracks().forEach(function (track) {
                        track.stop();
                    });
                    recordRTC.stream.getVideoTracks().forEach(function (track) {
                        track.stop();
                    });
                    recordRTC.stream = null;
                }

                recordRTC = null;
            }

            $scope.isPlayerReady = false;
            $scope.isRecording = false;
            $scope.isRecordingPaused = false;
            $scope.video = null;

            function destroy_video(video) {
                if (video != null) {
                    console.log("destroying video's blob", video);
                    URL.revokeObjectURL(video.bloburl);
                }
            }

            var reset_view = $scope.reset_view = function () {
                console.log("reset_view");
                $scope.discard_recording();

            };

            $scope.discard_movie = function () {
                console.log("discard_movie");
                if ($scope.video != null) {
                    destroy_video($scope.video);
                    $scope.video = null;
                }
            };

            $scope.upload_to_myfiles = function () {
                let video = $scope.video;
                console.log("upload_to_myfiles", video);

                return video.upload.do_upload()
                    .then(function (file) {
                        console.log("file has been uploaded", file);

                        return MyDialog.closeDialog()
                            .then(function () {
                                return $scope.on_close(file);
                            });

                    });
            };

            $scope.pause_recording = function () {
                console.log("pause_recording");

                $scope.isRecordingPaused = true;
                recordRTC.pauseRecording();
                recordRTC.total_time += (new Date().getTime() - recordRTC.start_time);

                $scope.player.recordIndicator.hide();
            };

            $scope.resume_recording = function () {
                console.log("resume_recording");

                $scope.isRecordingPaused = false;
                recordRTC.resumeRecording();
                recordRTC.start_time = new Date().getTime();

                $scope.player.recordIndicator.show();
            };

            $scope.stop_recording = function () {
                console.log("stop_recording");

                recordRTC.stopped_click++;
                if (recordRTC.stopped_click > 2) {
                    MyUtils.show_critical("%camera.recording_error%");
                    $scope.discard_recording();
                    $scope.click_start_recording();
                    return;
                }

                if ($scope.isRecordingPaused) {
                    $scope.resume_recording();
                }

                recordRTC.stopRecording(function (audioVideoWebMURL) {

                    recordRTC.total_time += (new Date().getTime() - recordRTC.start_time);
                    delete recordRTC.start_time;

                    const recording_title = $translator.translate("%rec_videos.new_title%");

                    var video = {
                        uuid: MyUtils.guid(),
                        weburl: $sce.trustAsResourceUrl(audioVideoWebMURL),
                        bloburl: audioVideoWebMURL,
                        blob: recordRTC.getBlob(),
                        time: new Date().getTime(),
                        title: recording_title + ' ' + $filter('asDate')(moment()),
                        video_options: recordRTC.stream.options,
                        duration_sec: Math.round(recordRTC.total_time / 1000)
                    };

                    video.upload = new MyUtils.StandardUploader(function () {
                        this.beforeUpload();

                        var uploader = this.uploader = Upload.upload({
                            url: '/api/file/upload',
                            data: {
                                file: video.blob,
                                file_info: {
                                    title: video.title,
                                    duration_sec: video.duration_sec,
                                }
                            }
                        });

                        var that = this;

                        return uploader
                            .then(
                                function (resp) {
                                    that.afterSuccess(resp);
                                    $scope.discard_movie(video);
                                    return resp.data;
                                },
                                function (err) {
                                    that.afterError(err);
                                    return $q.reject(err);
                                },
                                this.defaultOnProgress.bind(that))

                    });

                    console.log("recorded video", video);
                    $scope.video = video;
                    $scope.discard_recording();
                    $scope.$apply();

                });

            };

            $scope.discard_recording = function () {

                console.log("discard_recording");

                if ($scope.isRecordingPaused) {
                    $scope.resume_recording();
                }
                clear_RecordRTC();
                $scope.isRecording = false;
                $scope.videoRecording = false;
                if ($scope.player != null) {
                    $scope.player.recorder.reset();
                }
                $scope.isPlayerReady = false;

            };

            $scope.click_discard_recording = function () {
                console.log("click_discard_recording");
                $scope.discard_recording();
                $rootScope.recording = false;

                openWhenRecorderReady();
            };

            $scope.currentVideoBps = function () {
                return videoQualityToBitRate($scope.videoQuality);
            };

            function videoQualityToBitRate(quality) {
                if (quality === "High") {
                    return 1152000;
                }
                if (quality === "Medium") {
                    return 384000;
                }
                if (quality === "Low") {
                    return 128000;
                }
                return -1;
            }

            $scope.videoQuality = "Medium";
            $scope.start_recording = function () {

                console.log("start_recording, bitsPerSecond=", $scope.bitsPerSecond);

                if (recordRTC != null) {
                    throw new Error("not allowed, recording in progress");
                }

                var constraints = {
                    audio: true,
                    video: true
                };

                navigator.getUserMedia(constraints,
                    function (stream) {

                        var options = {
                            mimeType: 'video/webm',
                            bitsPerSecond: videoQualityToBitRate($scope.videoQuality),
                            disableLogs: false
                        };

                        recordRTC = new RecordRTC(stream, options);
                        recordRTC.stopped_click = 0;
                        recordRTC.stream = stream;
                        recordRTC.stream.options = options;
                        recordRTC.startRecording();
                        recordRTC.total_time = 0;
                        recordRTC.start_time = new Date().getTime();

                        var tick = function () {
                            if (!$scope.isRecordingPaused) {
                                $scope.videoLenght++;
                            }
                            if (recordRTC != null) {
                                $timeout(tick, 1000);
                            }
                        };

                        $scope.videoLenght = 0;
                        $timeout(tick, 1000);

                        $scope.player.recordIndicator.show();

                        $scope.isRecording = true;
                        $scope.isRecordingPaused = false;

                        $scope.$apply();

                    },
                    function (err) {
                        console.error('navigator.getUserMedia error: ', err);
                        MyUtils.show_alert("%camera.error%", err);
                    }
                );

            };

            $scope.clickOnCamera = function () {
                $timeout(function () {
                    console.log("simulating clicking on camera");
                    angular.element('.vjs-device-button').trigger('click');
                });
            };

            $scope.click_start_recording = function () {
                console.log("click_start_recording");

                if ($scope.videoRecording) {
                    console.warn("recording in progress");
                    return;
                }

                $scope.videoRecording = true;

                if ($scope.player != null) {
                    return;
                }

                if (videojs.getPlayers()['theRecorder2'] != null) {
                    console.warn("disposing player");
                    videojs.getPlayers()['theRecorder2'].dispose();
                }

                $scope.player = videojs("theRecorder2", {
                    controls: false,
                    loop: false,

                    plugins: {
                        record: {
                            audio: true,
                            video: true,

                            image: false,
                            debug: true
                        }
                    }
                });

                $scope.player.on('deviceReady', function () {
                    console.log('deviceReady');
                    $scope.isPlayerReady = true;
                    $scope.$apply();
                });

                console.log("theRecorder2 has been created");

            };

            let openWhenRecorderReady = function () {
                console.log("openWhenRecorderReady");
                if (angular.element("#theRecorder2").length === 0) {
                    $timeout(openWhenRecorderReady, 50);
                } else {
                    $scope.click_start_recording();
                }
            };

            openWhenRecorderReady();

            $scope.$on('$destroy', function () {
                $scope.discard_recording();
                $scope.discard_movie();
            });

        }
    );