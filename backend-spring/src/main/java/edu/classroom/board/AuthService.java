package edu.classroom.board;
// AuthService.java
// 로그인 인증과 JWT 발급·검증을 담당하는 핵심 서비스야.

// Users.java
// 사용자 정보와 테스트 계정 등 사용자 관련 데이터를 관리하는 코드인지 확인할 거야.

// BoardController.java
// 프론트엔드의 HTTP 요청을 받아 로그인, 게시글 조회, 등록, 수정, 삭제 등의 API를 처리하는 진입점이야.

// PostStore.java
// 게시글 저장과 조회, 수정, 삭제 등의 데이터 처리 로직을 담당하는지 살펴볼 거야.

// DatabaseConfig.java
// SQLite 연결 및 데이터베이스 관련 설정을 담당하는 파일이야.

// ApiError.java, Errors.java
// API 오류를 표현하고 처리하는 방식과 관련된 파일이야.

// BoardApplication.java
// Spring Boot 애플리케이션의 실행 시작점이야.

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;

// 게시판의 인증과 권환확인에 필요한 핵심 기능을 담당한다.
// 전처ㅔ적인 흐름: 로그인 요청: ID, PW 제출 -> issueToken(): 로그인 성공 시 JWT 발급 -> 게시글 수정/삭제 요청: HTTP 요청에 JWT 포함 -> currentUserID() 토큰 검증 및 사용자 ID 확인 ->  assertOwner(): 게시글 작성자와 비교하여 권한 확인 

@Service // Spring이 이 클래스를 서비스 컴포넌트로 관리하도록 지정 
// 인증 관련 메서드를 모아 둔 클래스 선언 
public class AuthService {
  // JJWT 의존성은 pom.xml에 포함되어 있습니다. app.jwt.* 설정은 application.properties 참조.
  // 로그인에 성공한 사용자에게 JWT 발급

  private final SecretKey key;
  private final Users users;
  private final String issuer, audience;
  private final int ttl;
  public AuthService(Users users, @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience,
      @Value("${app.jwt.ttl}") int ttl) {
    this.users = users; this.issuer = issuer; this.audience = audience; this.ttl = ttl;

    // 비밀키 검사
    if (secret.isBlank() || !secret.matches("[0-9a-fA-F]{64}"))
      throw new IllegalArgumentException("JWT_SECRET: 64자리 hex 필요");

    // 64자리 16진수를 32바이트 키로 변환
    key = Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret));

    // TTL 확인
    if (ttl < 1) throw new IllegalArgumentException("TOKEN_TTL must be positive"); // 설정값이 잘못되어 있다는것을 알리고 서버 초기화 실패.
  }

  public String issueToken(Users.PublicUser user) {
    // TODO BE-01: 서버 키로 HS256 JWT 발급. sub, exp, iss, aud 필수.
    // Users.PublicUser user: 토큰을 발급받을 사용자 정보
    // 반환: 발급한 JWT 문자열
    // sub: 토큰의 주체, 사용자 ID를 알리는 정도로 사용가능
    // exp: 토큰 만료 시각
    // iss: 토큰 발급자
    // aud  : 토큰의 대상 또는 수신자.


    Instant now = Instant.now();

    return Jwts.builder().subject(Long.toString(user.id()))
        .issuer(issuer).audience().add(audience).and()
        .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl)))
        .signWith(key, Jwts.SIG.HS256).compact();
  }

  // 현재 HTTP 요청을 보낸 사용자의 ID를 확인하는 역할
  public long currentUserId(HttpServletRequest request) {
    // TODO BE-02: Bearer 추출 → 서명/알고리즘/만료/클레임 검증 → 실제 사용자 확인.
    // 완성 후 아래 데모 ID 반환을 제거하세요. 토큰 없음/변조/만료는 401입니다.
    // request: 브라우저느 Vue 프론트엔드가 보낸 HTTP 요청
    // 완성 후 필요한 처리
    // 1. HTTP 요청 헤더에서 Authorization 값을 읽는다.
    // 2. Bearer 뒤에 있는 토큰 문자열을 추출한다.
    // 3. 서명과 허용 알고리즘, 만료 시간, 필수 클레임 등을 검증한다.
    // 4. 토큰의 사용자 ID가 실제 사용자에 해당하는지 확인한다.
    // 5. 검증된 사용자 ID를 반환한다.

    // HTTP 요청 헤더에서 Authorization 값 확인
    String header = request.getHeader("Authorization");

    if(header == null || !header.startsWith("Bearer ")) // Bearer 뒤에 공백하나와 JWT 문자열을 붙이므로
      throw new ApiError(401, "TOKEN_REQUIRED", "Bearer 형식 오류");

    // Bearer 뒤에 있는 토큰 문자열 추출
    String token = header.substring(7);// Bearer asdfasfawefasd... 앞의 7글자 날림.
    if(token.isEmpty())
      throw new ApiError(401, "TOKEN_REQUIRED", "토큰 누락");
    
    // JWT 검증
    try {
      // jwt 서명 및 클레임 검증
      var jws = Jwts.parser().verifyWith(key).requireIssuer(issuer)
          .requireAudience(audience).build().parseSignedClaims(token);
      var claims = jws.getPayload();

      // 허용 알고리즘 및 만료시간 확인 
      if (!"HS256".equals(jws.getHeader().getAlgorithm()) || claims.getExpiration() == null)
        throw new IllegalArgumentException();

      // 사용자 id 확인 
      String sub = claims.getSubject();

      if (sub == null || !sub.matches("[1-9][0-9]*")) throw new IllegalArgumentException();

      // 
      var userID = Long.parseLong(sub);

      if (users.find(userID) == null) throw new IllegalArgumentException();

      return userID;

    } catch (ExpiredJwtException e) {
      throw new ApiError(401, "TOKEN_EXPIRED", "정상 서명의 토큰이 만료됨");
    } catch (JwtException | IllegalArgumentException e) {
      throw new ApiError(401, "INVALID_TOKEN", "변조·서명·클레임 오류·없는 사용자");
    }
  }

  // 요청한 사용자와 게시글 작성자가 같은 사람인지 확인하는 역할
  public void assertOwner(long userId, long authorId) {
    // TODO BE-03: 다른 작성자이면 403 FORBIDDEN. 현재는 검사하지 않습니다.
    if(userId != authorId) throw new ApiError( 403, "FORBIDDEN", "인증한 사용자가 다른 사람 글을 변경");
  }
}
