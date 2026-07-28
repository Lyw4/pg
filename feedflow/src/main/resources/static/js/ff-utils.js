/*
 * FeedFlow - 공통 프론트엔드 유틸
 *
 *  barcode-scan.js / outbound-preview.js 에 중복되어 있던
 *  escapeHtml / number / dDayBadge 를 한 곳으로 모았다.
 *  (D-Day 표기 규칙은 서버의 com.feedflow.common.util.DDay 와 동일하게 유지한다)
 */
window.FFUtils = (function () {
    'use strict';

    /** XSS 방지 - 서버 응답 문자열을 DOM 에 넣기 전에 반드시 통과시킨다 */
    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** 천 단위 구분 (null 이면 '-') */
    function number(value) {
        if (value === null || value === undefined) {
            return '-';
        }
        return Number(value).toLocaleString('ko-KR');
    }

    /**
     * D-Day 라벨 + 뱃지 클래스
     *  · 음수 : 만료 N일 경과 (검정)
     *  · 0    : 오늘 만료 (빨강)
     *  · 1~7  : 빨강 / 8~30 : 노랑 / 31+ : 기본
     */
    function dDayBadge(remainingDays) {
        if (remainingDays === null || remainingDays === undefined) {
            return {label: '-', cls: 'bg-light text-dark border'};
        }
        if (remainingDays < 0) {
            return {label: '만료 ' + Math.abs(remainingDays) + '일 경과', cls: 'bg-dark'};
        }
        if (remainingDays === 0) {
            return {label: '오늘 만료', cls: 'bg-danger'};
        }
        if (remainingDays <= 7) {
            return {label: 'D-' + remainingDays, cls: 'bg-danger'};
        }
        if (remainingDays <= 30) {
            return {label: 'D-' + remainingDays, cls: 'bg-warning text-dark'};
        }
        return {label: 'D-' + remainingDays, cls: 'bg-light text-dark border'};
    }

    return {
        escapeHtml: escapeHtml,
        number: number,
        dDayBadge: dDayBadge
    };
})();
