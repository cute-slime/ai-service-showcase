# workflow-template-transparent-draft.json 사용 가이드

이 파일은 `configJson`으로 등록하기 위한 **캐릭터 누끼(투명 PNG) 워크플로우 초안**입니다.

## 1. 반드시 교체할 항목

- `__REMOVE_BG_NODE__`
  - 현재 설치된 ComfyUI 배경 제거 노드의 `class_type`으로 교체하세요.
  - 예: RMBG/BiRefNet 계열 노드

## 2. 입력 키 확인

- 노드마다 입력 키 이름이 다릅니다.
- 초안은 `inputs.image`를 기준으로 작성되어 있으므로, 실제 노드 입력 키에 맞게 수정하세요.
  - 예: `image`, `images`, `input_image` 등

## 3. 플레이스홀더

- `{{PROMPT}}`, `{{WIDTH}}`, `{{HEIGHT}}`, `{{SEED}}`는 ai-service에서 런타임에 치환됩니다.
- 치환 로직: `ComfyUIProvider.buildWorkflowFromModel(...)`

## 4. 연결 규칙

- `80`번 노드(배경 제거) 출력이 `81`번 `SaveImage`로 연결되어야 투명 PNG로 저장됩니다.
- 배경 제거 노드가 마스크와 이미지를 분리 출력한다면, `SaveImage`에 알파가 적용된 이미지 출력 포트를 연결하세요.

## 5. 적용 권장 순서

1. ComfyUI UI에서 실제로 동작하는 누끼 그래프를 먼저 완성
2. API 포맷(JSON)으로 Export
3. Export 결과를 `GenerationProviderModel.configJson`에 저장
4. `{{...}}` 플레이스홀더만 적용

