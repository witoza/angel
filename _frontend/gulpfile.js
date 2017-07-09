'use strict';

var gulp = require('gulp');

// plugins
var minifyCSS = require('gulp-minify-css');
var clean = require('gulp-clean');
var runSequence = require('run-sequence');
var replace = require('gulp-token-replace');

gulp.task('clean', function () {
    gulp.src('./dist')
        .pipe(clean({force: true}));
});

gulp.task('minify-css', function () {
    var opts = {comments: true, spare: true};
    gulp.src(['./app/**/*.css', '!./app/_provided/**'])
        .pipe(minifyCSS(opts))
        .pipe(gulp.dest('./dist/'))
});

gulp.task('copy-files', function () {
    gulp.src([
        '!./app/**/*.html',
        './app/**/*.png',
        './app/**/*.ico',
        './app/**/*.jpg',
        '!./app/dist/**',
        '!./app/_provided/**'])
        .pipe(gulp.dest('dist/'));

    gulp.src([
        './app/dist/**'])
        .pipe(gulp.dest('dist/dist'));


    gulp.src([
        './app/_provided/**'])
        .pipe(gulp.dest('dist/_provided'));
});

gulp.task('token-replace', function () {
    var config = {
        ts: Date.now()
    };
    return gulp.src(['./app/**/*.html'])
        .pipe(replace({
            prefix: "$$",
            suffix: "$$",
            preserveUnknownTokens: true,
            global: config
        }))
        .pipe(gulp.dest('dist/'));
});

gulp.task('build', function () {
    runSequence(
        ['clean'],
        ['minify-css', 'copy-files'],
        ['token-replace']
    );
});
