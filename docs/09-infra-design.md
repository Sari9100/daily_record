# 가족 생활기록 시스템 — 인프라 / 배포 설계 (v1)

> 기준: 기능명세 v4, DB설계 v3, 인증설계 v1
> 환경: 로컬 서버(Mac Mini M4 home server) + 외장 SSD + Cloudflare
> 원칙: **Local-First, Cloud-Portable** — 로컬 구축, 클라우드 이식 대비

---

## 1. 전체 구성 개요

```
[iPhone/Galaxy 앱]  [웹 브라우저]
        │                │
        └──── HTTPS ──────┘
                │
        [Cloudflare] (도메인·TLS·Zero Trust·터널)
                │  (cloudflared tunnel)
        ┌───────┴────────────────────┐
        │   Mac Mini M4 (home server) │
        │  ┌────────────────────────┐ │
        │  │ Docker Compose          │ │
        │  │  - api (Spring Boot)    │ │
        │  │  - mysql                │ │
        │  │  - nginx (리버스프록시) │ │
        │  │  - cloudflared          │ │
        │  └────────────────────────┘ │
        │  외장 SSD (사진 원본)        │
        │  백업 SSD (사진·DB 백업)     │
        └─────────────────────────────┘
```

- 기존 stock-account 운영 패턴(Docker, Nginx, deploy.sh, Cloudflare) 재활용
- 도메인 예: `family.saristock.com`(앱/웹), `api.saristock.com`(백엔드)

---

## 2. 컨테이너 구성 (Docker Compose)

| 서비스 | 이미지 | 역할 |
|--------|--------|------|
| api | 자체 빌드(Spring Boot 4.1, Java 21) | 백엔드 API |
| mysql | mysql:8.x | DB (기존 서버에 family_os 스키마) |
| nginx | nginx | 리버스 프록시, 정적 사진 서빙(내부) |
| cloudflared | cloudflare/cloudflared | Zero Trust 터널 |

설계 포인트:
- **환경변수 외부화**(.env): DB접속, JWT시크릿, 구글 OAuth, 저장경로 — 코드 하드코딩 금지
- 외장 SSD를 api/nginx 컨테이너에 **볼륨 마운트** (`/mnt/ssd/family_os/photos`)
- MySQL 데이터도 볼륨 영속화
- 헬스체크: `/actuator/health` (인증 면제 경로)

```yaml
# docker-compose.yml (개략)
services:
  api:
    build: ./api
    env_file: .env
    volumes:
      - /mnt/ssd/family_os/photos:/data/photos
    depends_on: [mysql]
  mysql:
    image: mysql:8.4
    env_file: .env.mysql
    volumes:
      - /mnt/ssd/family_os/mysql:/var/lib/mysql
  nginx:
    image: nginx
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on: [api]
  cloudflared:
    image: cloudflare/cloudflared
    command: tunnel run
    env_file: .env.cf
```

---

## 3. StorageService (사진 저장 추상화)

### 3-1. 인터페이스
```java
public interface StorageService {
    StoredObject store(String key, InputStream data, String mimeType);  // 업로드
    InputStream load(String key);                                       // 조회
    void delete(String key);                                            // soft 후 물리(보류)
    URI presignUpload(String key, Duration ttl);                        // 업로드 URL(선택)
    URI presignDownload(String key, Duration ttl);                      // 서명 조회 URL
}
```

### 3-2. 구현체
| 구현 | 시점 | 저장 위치 |
|------|------|-----------|
| **LocalSsdStorage** | 지금(MVP) | 외장 SSD `/data/photos/{key}` |
| ObjectStorage(R2/S3) | 클라우드 전환 | 객체스토리지 (동일 인터페이스) |

### 3-3. 키 규칙 (상대 키)
```
family_{familyId}/{yyyy}/{MM}/{uuid}.{ext}
예: family_1/2026/06/a1b2c3.jpg
```
- DB(photo.storage_key)엔 **상대 키만** 저장 (절대경로 금지) → 스토리지 교체 시 무변경
- 물리 경로 = 마운트 베이스(`/data/photos`) + 상대 키

### 3-4. 업로드 흐름 (로컬 환경)
- presign 방식보다 **서버 경유 업로드**가 로컬 SSD 환경에 단순·안전
  - presign(API): photoId·storage_key 발급
  - 업로드: 앱 → 백엔드 → SSD 기록 (Nginx body size 제한 조정)
  - complete: width/height·photo_hash 확정
- 조회: 서명된 URL 또는 인증된 스트리밍(family_id·visibility 검증 후 서빙)

### 3-5. 사진 서빙 보안
- **사진도 권한 통제 대상** — 직접 URL 노출 금지
- Nginx `internal` + 백엔드 인증 후 X-Accel-Redirect, 또는 단기 서명 URL
- family_id·visibility 검증 통과한 요청만 바이너리 전달

---

## 4. Cloudflare

| 기능 | 용도 |
|------|------|
| DNS·TLS | 도메인, HTTPS 자동 |
| Tunnel(cloudflared) | 공인 IP 없이 홈서버 노출(포트 개방 불필요) |
| Zero Trust | 접근 통제(가족만). 단 **푸시 webhook 경로는 공개 예외** |
| WAF/Rate Limit | 기본 보호 |

주의:
- 구글 푸시 알림 수신 URL(`/integrations/google/notifications`)은 **Zero Trust 인증 예외**로 공개
  (구글 서버가 인증 없이 POST하므로) — 대신 X-Goog-Channel-Token 검증으로 보호
- 그 외 모든 경로는 Zero Trust + JWT 이중

---

## 5. 백업 전략 (양보 불가 — 자동화)

> "DB가 사진보다 먼저 복구돼야 한다" — DB 없으면 사진 파일이 멀쩡해도 의미 부여 불가

### 5-1. DB 백업 (최우선)
| 항목 | 정책 |
|------|------|
| 방식 | `mysqldump` (또는 `xtrabackup`) |
| 주기 | **매일 새벽**(cron/launchd) |
| 보관 | **7일 롤링** + **월 1회 장기보관** |
| 위치 | 백업 SSD + (가능하면) R2 오프사이트 1벌 |
| 검증 | 주기적 복원 테스트(분기 1회 권장) |

```
# 개략 (launchd 또는 cron)
mysqldump --single-transaction family_os | gzip > backup/db/family_os_$(date +%F).sql.gz
# 7일 초과 삭제, 매월 1일분은 monthly/로 이동
```

### 5-2. 사진 백업 (2중화)
| 단계 | 내용 |
|------|------|
| 원본 | 외장 SSD `/data/photos` |
| 백업 | 백업 SSD로 `rsync` (매일/실시간) |
| 오프사이트 | (권장) R2 동기화 1벌 — 화재·도난 대비 |

- `photo_hash`로 무결성 검증, 중복 제거
- rsync `--checksum` 주기 정합성 점검

### 5-3. 복구 우선순위
```
1) DB 복원 (구조·메타데이터)
2) 사진 파일 복원 (storage_key로 매칭)
3) 정합성 점검 (photo_hash, DB-파일 매칭)
```

---

## 6. 배포 / 운영

### 6-1. 빌드·배포
- Gradle 빌드 → Docker 이미지 → Compose 재기동
- `deploy.sh` 패턴 재활용(기존 자산): pull/build/up
- 무중단까진 불필요(가족 규모) — 짧은 재기동 허용

### 6-2. 마이그레이션 (Flyway)
- 앱 기동 시 Flyway 자동 실행(`V1__init.sql` → 이후 V2, V3...)
- 운영 DB는 **백업 후 마이그레이션** 원칙
- 시딩: 가족 생성 시 기본 카테고리(V2 또는 앱 로직)

### 6-3. 로그·모니터링
- 앱 로그: 파일 + (선택) 수집
- `/actuator/health`, `/actuator/metrics` (인증 통제)
- 디스크 용량 경보(사진 누적) — SSD 임계치 알림

### 6-4. 시크릿 관리
- `.env` 파일 권한 제한, git 제외
- JWT 시크릿, 구글 OAuth, refresh token 암호화 키 — 환경변수
- 클라우드 전환 시 시크릿 매니저로 이전

---

## 7. 클라우드 전환 대비 (B 단계)

| 로컬(현재) | 클라우드(B) |
|-----------|-------------|
| Mac Mini Docker | 클라우드 VM/컨테이너 |
| 로컬 MySQL | 관리형 MySQL(RDS 등) |
| 외장 SSD + LocalSsdStorage | R2/S3 + ObjectStorage |
| 수동 백업 SSD | 관리형 자동 백업 |
| cloudflared 터널 | 정식 도메인·로드밸런서 |

- StorageService·환경변수 외부화·Docker화로 **코드 변경 최소**
- 전환 트리거: 동생네 오픈(2단계) 또는 정식 수익화(3단계)

---

## 8. 열린 항목
1. 사진 서빙 방식 확정(X-Accel-Redirect vs 서명 URL)
2. R2 오프사이트 백업 도입 여부·비용
3. launchd vs cron (macOS 환경 — launchd 권장, 기존 Hermes 패턴)
4. 디스크 용량 모니터링·경보 수단
5. Flyway 운영 마이그레이션 절차(백업 연동 자동화)

---

## 9. 문서 세트 완료
본 문서로 설계 문서 세트(우선순위 1~6) 완료:
1. 기능명세 v4 / 2. DB설계 v3 / 3. JPA v1 / 4. DDL / 5. API v1 /
6. 인증 v1 / 7. 프론트 v1 / 8. 외부연동 v1 / 9. 인프라 v1 / (+ 통합 마스터)

**다음: 구현 착수** — 구현순서 1단계(코어: 인물·가계부·일정·일상기록·타임라인)부터.
