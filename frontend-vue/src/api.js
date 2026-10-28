// TODO FE-01~04: 공통 API 명세와 구현 체크리스트를 보고 완성하세요.
// 로그인 상태를 저장하는 변수( 발급받은 JWT, 로그인한 사용자 정보)
let session = { accessToken: "", user: null };
// 현재 로그인 상태를 반환하는 함수, session안에 저장된 사용자 정보를 가져와 vue화면에서 사용하는 user에 넣음 
export const getSession = () => session; 
// 로그인 상태 초기화, 로그아웃 버튼 누르면 이 함수 호출
export function clearSession() {
  session = { accessToken: "", user: null };
}
// 인증 헤더를 만드는 함수. 
function authorizationHeaders() {
  // TODO FE-02: 메모리에 보관한 토큰을 Bearer 헤더로 보내세요.
  
  // 토큰이 없으면 헤더를 만들지 않음. 
  if(!session.accessToken)
    return {};

  // 토큰이 있으면 Authorization: Bearer <JWT> 형태로 보냄
  return {
    "Authorization": "Bearer " + session.accessToken
  };
}
// 여러 곳에서 반복되는 HTTP 요청 코드를 한곳으로 모음. 
export async function request(path, options = {}) {// 매개변수: path(API 경로), option(HTTP메서드, 요청 본문, 추가 헤더 등) ex request("/posts", {method: "POST", body: "{}"});
  // fetch()로 HTTP 요청을 보냄. fetch(): 브라우저가 제공하는 HTTP 통신 함수.
  const response = await fetch(`/api${path}`, {
    ...options, // js의 객체 전개 문법. 객체가 있다면 그 객체의 속성을 현재 객체에 펼쳐 넣는 역할. 
    headers: { // 요청에 필요한 헤더를 한곳에서 합침
      "Content-Type": "application/json", // JSON 본문을 전송한다는 의미.
      ...authorizationHeaders(),// 인증 토큰 헤더를 추가할 자리
      ...options.headers, // 특정 요청에만 필요한 추가 헤더.
    },
  });
  // 서버 응답처리. HTTP 상태코드가 204라면 응답 본문이 없는 것으로 보고 null을 반환. 다른 응답 코드는 응답 본문을 JSON으로 변환해 data에 저장. 
  const data = response.status === 204 ? null : await response.json();
  // response.ok (200 ~ 299일때 true), 성공 범위가 아니라면 오류객체 발생. 
  if (!response.ok) {
    
    // TODO FE-03: 401이면 메모리의 로그인 상태도 해제하세요.
    if(response.status === 401)
      clearSession();
    
    const error = new Error(
      data.message || data.code || `HTTP ${response.status}`,
    );
    error.status = response.status;
    error.code = data.code;
    throw error;
  }

  return data;
}
export async function login(email, password) {
  // TODO FE-01: POST /auth/login → 응답의 토큰과 사용자 정보를 session에 저장.
  // request()를 이용할 수 있습니다. 성공하면 user를 반환하세요.

  // 로그인 폼의 email·password를 JSON으로 POST /api/auth/login에 보냄
  const data = await request("/auth/login", {
    method: "POST",
    body: JSON.stringify({email, password}) // 이메일과 비밀번호를 JSON 문자열로 변환(실습 1)
  });

  // 성공 응답의 accessToken과 user를 session에 저장.
  session = {
    accessToken: data.accessToken,
    user: data.user,
  };

  // user 반환 
  return data.user;
}
// 특정 게시글의 수정/삭제 버튼을 화면에 보여 줄지 결정하는 역할
export function canEdit(post) { //post: 게시글 하나의 정보를 담고 있는 객체
  // TODO FE-04: 로그인 사용자와 작성자를 비교하세요. 서버 검사도 반드시 필요합니다.
  // 로그인한 사용자 있는지 확인. 
  // 두 ID가 같은지 비교
  // true/ false 반환 
  const user = session.user;

  if(user == null || post == null) return false;

  return user.id === post.authorId;
}
// api 객체
export const api = {
  list: () => request("/posts"), // api.list() -> GET -> 게시글 목록 조회
  detail: (id) => request(`/posts/${id}`), // api.detail(id) -> GET - > 특정 게시글 조회
  create: (input) => 
    request("/posts", { method: "POST", body: JSON.stringify(input) }),// api.create(input) -> POST -> 게시글 작성
  update: (id, input) =>
    request(`/posts/${id}`, { method: "PUT", body: JSON.stringify(input) }), // api.update(id.input) -> PUT -> 게시글 수정
  remove: (id) => request(`/posts/${id}`, { method: "DELETE" }), // api.remove(id) -> DELETE -> 게시글 삭제 
  me: () => request("/me"), // api.me() -> GET -> 로그인한 사용자 정보 조회
};
