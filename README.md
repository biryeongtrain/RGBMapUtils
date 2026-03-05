# RGBMapLibs

JNNGL `rgb_maps` 아이디어를 Fabric 모드에서 재사용 가능한 Java 라이브러리로 포팅한 프로젝트입니다.

## 포함 내용
- `kim.biryeong.maprgbutils.api.RgbMapCodec`: 64x64 `0xRRGGBB` <-> 128x128 map index(0..127) 인코딩/디코딩 API
- `kim.biryeong.maprgbutils.impl.RgbMapCodecImpl`: 참조 구현과 동일한 비트 배치 알고리즘 구현체
- `kim.biryeong.maprgbutils.api.RgbMapPalette`: 128개 lookup 팔레트 및 디버그용 RGB 변환
- `kim.biryeong.maprgbutils.api.RgbMapCanvasAdapter`: MapCanvas `DrawableCanvas` / `BufferedImage` 전환 유틸
- `kim.biryeong.maprgbutils.api.RgbMapCombinedCanvasAdapter`: `CombinedPlayerCanvas` 탑레벨 인코딩 유틸
- `assets/minecraft/shaders/core/rendertype_text.fsh`: `rgb_maps` 셰이더 파일 포함

## 사용 절차
1. 서버/모드 코드에서 64x64 이미지를 인코딩합니다.
2. 인코딩 결과(128x128, 16384개 인덱스 0..127)를 맵 데이터에 반영합니다.
3. 서버 시작 시 Polymer 리소스팩(`polymer/resource_pack.zip`)이 자동 생성됩니다. 실패 시 `/generate-pack`으로 수동 재생성합니다.
4. 클라이언트에서 Polymer가 제공하는 서버 리소스팩을 활성화합니다.
5. `rendertype_text.fsh`가 적용된 상태에서 맵이 원본 RGB처럼 복원되어 렌더링됩니다.

## 개발용 디버그 커맨드
- 개발 환경(`FabricLoader#isDevelopmentEnvironment() == true`)일 때만 `/rgbmapdebug` 커맨드가 등록됩니다.
- `/rgbmapdebug`: `src/main/resources/img2.png`를 원본 크기 기준(128x128 표시 타일)으로 월드에 표시합니다.
  - 인벤토리 지급 없이 `VirtualDisplay`로 플레이어가 보는 위치의 블록 면에 맵 패널 2개를 나란히 띄웁니다.
  - 왼쪽: 원본 `BufferedImage`를 `MapCanvas`로만 변환한 멀티맵(팔레트 근사)
  - 오른쪽: 원본 크기 유지를 위해 각 `128x128` 구간을 `64x64`로 축소 후 `RgbMapCodec` 인코딩한 멀티맵
  - 이미지가 `128`의 배수가 아니면 마지막 타일 구간은 비율에 맞춰 축소 샘플링됩니다.
- `/rgbmapdebug clear`: 현재 플레이어의 디버그 월드 표시를 제거합니다.

## 예시
```java
RgbMapCodec codec = RgbMapCodec.createDefault();
int[] mapIndexes = codec.encodeImageToMapIndexes(image64x64);

// mapIndexes를 맵 데이터에 반영 (0..127)
byte[] mapBytes = new byte[mapIndexes.length];
for (int i = 0; i < mapIndexes.length; i++) {
    // Minecraft raw map color byte에서 0..3은 clear 슬롯이므로 +4 오프셋이 필요합니다.
    mapBytes[i] = (byte) (mapIndexes[i] + 4);
}
```

```java
// BufferedImage -> RGB 인코딩 멀티맵 (원본 표시 크기 유지)
CombinedPlayerCanvas rgbCombined = RgbMapCombinedCanvasAdapter.encodeImageToRgbMapCombinedCanvas(sourceImage);

// DrawableCanvas -> RGB 인코딩 멀티맵
CombinedPlayerCanvas rgbFromCanvas = RgbMapCombinedCanvasAdapter.encodeCanvasToRgbMapCombinedCanvas(sourceCanvas);
```

## 참고
- 모드 초기화 시 `ResourcePackBuilder` pre-finish 패치로 `assets/minecraft/shaders/core/rendertype_text.fsh` 최종 결과물을 병합 생성합니다.
- 셰이더 원본을 읽을 수 없는 환경에서는 모드에 포함된 fallback 셰이더를 pack에 기록합니다.
- 이 방식은 ShaderFX처럼 동일 셰이더를 수정하는 모드와 공존할 때, 파일 통덮어쓰기보다 충돌 위험을 낮춥니다.
- 클라이언트가 팩을 비활성화하면 RGB 복원 셰이더가 적용되지 않아 일반 맵 색으로 보입니다.
