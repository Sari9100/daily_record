# Family OS — 설계 문서 세트 (README)

가족 생활기록 시스템(가계부 + 일상기록 + 일정관리)의 전체 설계 문서 모음입니다.

## 읽는 순서

처음 보는 경우 **00-master.md**(통합 마스터)부터 읽으면 전체를 조망할 수 있습니다.
세부가 필요하면 아래 번호 순서대로 내려갑니다.

| # | 문서 | 내용 |
|---|------|------|
| 00 | master | 전체 통합 (목차·요약) |
| 01 | functional-spec | 기능 명세 (무엇을 만드나) |
| 02 | db-design | DB 설계 (테이블·컬럼·인덱스) |
| 03 | jpa-entity | JPA Entity 매핑·ORM 전략 |
| 04 | V1__init.sql | 초기 DDL (21테이블, MySQL 방언 파싱 검증) |
| 05 | api-spec | REST API 명세 |
| 06 | auth-design | 인증(JWT)·인가(visibility) |
| 07 | frontend-design | React Native + Expo 화면·구조 |
| 08 | integration-design | 구글 캘린더 양방향·텔레그램 |
| 09 | infra-design | Docker·Cloudflare·백업 |

## 현재 진행 상태

- ✅ 설계 문서 1~9 + 통합 마스터 완료
- ⬜ 백엔드 패키지/레이어 구조 (구현 직전 작성)
- ⬜ 구현 착수

## 확정된 핵심 결정 (빠른 참조)

- **스택**: Spring Boot 4.1 + Java 21 / JPA+MyBatis / MySQL / Flyway / Spring Security 7(JWT)
- **프론트**: React Native + Expo (웹=react-native-web), gluestack-ui(NativeWind), 로직 공유+UI 분기
- **인프라**: Mac Mini 로컬 서버 + 외장 SSD + Cloudflare Tunnel, Local-First·Cloud-Portable
- **인물 모델**: Person / UserAccount 분리 (자녀 전환 시 데이터 이관 0)
- **권한**: author / subject / visibility 3분리, family_id 멀티테넌트
- **가계부**: INCOME/EXPENSE/TRANSFER, source/target 계좌, 개인카드 선지출→공용계좌 정산(B)
- **핵심 차별화**: 통합 타임라인 (지출+일정+기록+사진 날짜축 병합)

## 구현 순서 (MVP)

1. **코어** — 인물·가계부·일정·일상기록 직접입력·통합 타임라인
2. **텔레그램** 입력 (Hermes 확장)
3. **구글 캘린더** 양방향 동기화 (가장 까다로움, 마지막)

→ V1.5: 통합 검색 / V2: AI 리포트·육아 마일스톤

## 로드맵 (확장)

1. MVP (로컬, 우리 가족)
2. 동생네 오픈 (격리·백업·인증 강화)
3. 정식 수익화 (클라우드 전환 — 별도 사업 영역)
