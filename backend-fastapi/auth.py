from errors import ApiError


# PyJWT는 requirements.txt에 포함되어 있습니다. 아래 함수를 완성하세요.
def issue_token(user):
    # TODO BE-01: 서버 환경변수 키로 HS256 JWT 발급. sub, exp, iss, aud 필수.
    raise ApiError(501, "NOT_IMPLEMENTED", "JWT 발급을 구현하세요. BE-01")


def current_user_id(request):
    # TODO BE-02: Bearer 추출 → 서명/알고리즘/만료/클레임 검증 → 실제 사용자 확인.
    # 완성 후 데모 ID 반환을 제거하세요. 토큰 없음/변조/만료는 401입니다.
    return 1


def assert_owner(user_id, author_id):
    # TODO BE-03: 다른 작성자이면 403 FORBIDDEN. 현재는 검사하지 않습니다.
    pass
