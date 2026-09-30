package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 불량 유형 (무엇이 잘못되었는가).
 *
 * <h3>{@link DisposalReason} 과 값이 겹치는데 왜 따로 두는가</h3>
 * 네 개가 겹칩니다 — 파손 · 오염 · 유통기한 · 기타. 그래서 하나로 합칠까 고민했지만
 * <b>묻는 것이 다릅니다.</b>
 * <ul>
 *     <li>{@code DefectType} — <b>무엇이 잘못되었는가.</b> 불량을 발견한 시점에 적는다.</li>
 *     <li>{@link DisposalReason} — <b>왜 버리는가.</b> 폐기를 결정한 시점에 적는다.</li>
 * </ul>
 * <b>불량이 반드시 폐기로 가지 않는다</b>는 것이 핵심입니다. 규격 미달이어도
 * {@link DefectResolution#CONCESSION}(특채)으로 쓸 수 있고,
 * {@link DefectResolution#REWORK}(재작업) 후 정상 재고로 돌아올 수도 있습니다.
 * 반대로 {@code DisposalReason} 에는 불량과 무관한 값이 있습니다 —
 * 재고 실사 손실, 품질검사 샘플 사용.
 * <p>
 * 두 값을 한 enum 으로 합치면 "재고 실사 손실" 이라는 <b>불량 유형</b>이 생기고,
 * "규격 미달" 이라는 <b>폐기 사유</b>가 생깁니다. 각 화면에서 쓰지 않는 값을
 * 골라내는 조건이 붙기 시작합니다.
 * <p>
 * 대신 불량을 폐기로 처리할 때는 {@link #toDisposalReason()} 으로 옮깁니다.
 * 담당자가 같은 내용을 두 번 고르지 않게 하려는 것입니다.
 */
@Getter
@RequiredArgsConstructor
public enum DefectType {

    DAMAGE("파손 / 포장 손상", "bg-warning text-dark", DisposalReason.DAMAGED),
    CONTAMINATION("변질 / 오염", "bg-danger", DisposalReason.CONTAMINATED),
    WET("침수 / 습기", "bg-danger", DisposalReason.WET),
    SPECIFICATION("규격 미달", "bg-secondary", DisposalReason.OTHER),
    FOREIGN_MATTER("이물 혼입", "bg-danger", DisposalReason.CONTAMINATED),
    EXPIRED("유통기한 경과", "bg-dark", DisposalReason.EXPIRED),
    OTHER("기타", "bg-secondary", DisposalReason.OTHER);

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 이 불량을 폐기로 처리할 때 쓸 폐기 사유.
     * <p>
     * 규격 미달은 대응하는 폐기 사유가 없어 {@code OTHER} 로 보냅니다.
     * 폐기 사유에 '규격 미달' 을 추가할 수도 있지만, 규격 미달은 대개 특채나
     * 반품으로 처리되고 폐기까지 가는 경우가 드물어 값을 늘리지 않았습니다.
     */
    private final DisposalReason disposalReason;

    /**
     * 공급업체 반품을 검토할 만한 유형인지.
     * <p>
     * 파손 · 규격 미달 · 이물 혼입은 <b>제조 · 운송 과정의 문제</b>라 공급업체 책임을
     * 물을 수 있습니다. 반면 유통기한 경과는 우리 재고 관리 문제이고, 침수는
     * 보관 환경 문제일 가능성이 높아 반품 대상으로 보기 어렵습니다.
     * <p>
     * 이 판단은 <b>강제가 아니라 안내</b>입니다. 실제 책임 소재는 사람이 정하므로
     * 화면에서 권장만 하고 다른 처리를 막지 않습니다.
     */
    public boolean isSupplierReturnCandidate() {
        return this == DAMAGE || this == SPECIFICATION || this == FOREIGN_MATTER;
    }
}
