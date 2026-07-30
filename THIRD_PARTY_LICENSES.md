# Third-party license notices

Power Clock bundles or links against the following open-source components.
All Power Clock code, artwork, branding, and alarm sounds are first-party and
original to this project; the bundled typefaces are open-source and credited
below.

| Component | Copyright | License |
| --- | --- | --- |
| AndroidX libraries (Core KTX, AppCompat, Lifecycle, Activity, Navigation, Room, DataStore, CameraX, Media3, SplashScreen, Compose UI/Foundation/Material 3, Material Icons) | The Android Open Source Project | Apache License 2.0 |
| Kotlin Standard Library | JetBrains s.r.o. and Kotlin contributors | Apache License 2.0 |
| kotlinx.coroutines | JetBrains s.r.o. | Apache License 2.0 |
| Dagger / Hilt | The Dagger Authors / Google LLC | Apache License 2.0 |
| MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) | Google LLC | Apache License 2.0 |
| MediaPipe Pose Landmarker Lite model (`pose_landmarker_lite.task`) | Google LLC | Apache License 2.0 |
| ZXing ("Zebra Crossing") core | ZXing authors | Apache License 2.0 |
| Sora typeface (`res/font/sora_*.ttf`) | Jonathan Barnabé / Sora Project Authors | SIL Open Font License 1.1 |
| Inter typeface (`res/font/inter_*.ttf`) | The Inter Project Authors | SIL Open Font License 1.1 |
| JUnit 4 (tests only) | JUnit contributors | Eclipse Public License 1.0 |
| Google Truth (tests only) | Google LLC | Apache License 2.0 |

## Apache License 2.0

The full text of the Apache License, Version 2.0 is available at:
<https://www.apache.org/licenses/LICENSE-2.0>

> Licensed under the Apache License, Version 2.0 (the "License"); you may
> not use these files except in compliance with the License. Unless required
> by applicable law or agreed to in writing, software distributed under the
> License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
> CONDITIONS OF ANY KIND, either express or implied. See the License for the
> specific language governing permissions and limitations under the License.

## Eclipse Public License 1.0 (JUnit, test-only dependency)

Full text: <https://www.eclipse.org/legal/epl-v10.html>. JUnit is used only
in the test source sets and is not distributed inside the APK.

## SIL Open Font License 1.1 (bundled typefaces)

Full text: <https://openfontlicense.org/open-font-license-official-text/>.
The OFL permits bundling and redistribution inside an application, including
commercially, provided the fonts themselves are not sold on their own and the
copyright notice is retained.

Both families were built from the official Google Fonts sources; the static
weights shipped here (Sora Regular/SemiBold/Bold, Inter Regular/Medium/
SemiBold) were instanced from the upstream variable fonts without any
modification to the outlines:

- `https://github.com/google/fonts/blob/main/ofl/sora/Sora%5Bwght%5D.ttf`
- `https://github.com/google/fonts/blob/main/ofl/inter/Inter%5Bopsz,wght%5D.ttf`

## Model provenance

`app/src/main/assets/pose_landmarker_lite.task` was downloaded from Google's
official MediaPipe model repository:
`https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task`
and is redistributed under Apache License 2.0 in accordance with its model
card.

## Original first-party assets

- The Power Clock name, logo, adaptive icons, and wordmark are original. The
  typefaces used to set them are OFL-licensed and credited above.
- All eight alarm tones were synthesized from scratch by
  `tools/generate_sounds.py` (pure numpy synthesis) for this project and are
  royalty-free.
