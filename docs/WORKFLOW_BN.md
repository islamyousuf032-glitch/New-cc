# Owncut: অ্যাপ ও কাজের প্রবাহ — নকশা v1

**তারিখ: ২২ অক্টোবর ২০২৬।** এটি পরিবর্তনযোগ্য ভিত্তি; সব advanced feature এখনই তৈরি নয়।
বিস্তারিত engineering চুক্তি: [Advanced architecture](ADVANCED_ARCHITECTURE.md)।
সিদ্ধান্ত: [ADR 0009](DECISIONS/0009-project-workspaces-and-node-architecture.md)।

## ১. অ্যাপ খুললে

প্রথম পর্দা **Projects**। এখানে আগে তৈরি/import করা project-এর নাম, শেষ খোলা/রাখার
সময় এবং recovery snapshot আছে কি না দেখা যাবে। নতুন project, পুরোনো `.owncut`
file import, project খোলা ও নাম বদলানো যাবে। তালিকা refresh করার ব্যবস্থাও আছে।

- **নতুন project:** একটি নাম দিন → আলাদা local project তৈরি হবে → editor খুলবে।
- **আগের কাজ:** তার card-এর “খুলুন” চাপুন → saved project editor-এ আসবে।
- **Project file import:** file বেছে নাম দিন → যাচাই করে নতুন local copy তৈরি হবে।
  বাইরের original file বদলাবে না। এই file-এর মধ্যে মূল ভিডিওগুলো প্যাক করা নেই;
  original media রেখে দিতে হবে, প্রয়োজনে relink করতে হবে।
- **নাম বদল:** library-তে দেখানো নাম বদলাবে; clip বা source file বদলাবে না।
- নতুন ব্যবস্থার আগে app-এর ভেতরে থাকা workspace থাকলে original না মুছে আলাদা
  entry-তে নেওয়া হবে। Corrupt file চুপচাপ মুছে/খালি project দিয়ে প্রতিস্থাপন হবে না।

Local Projects তালিকা কোনো cloud backup নয়। Uninstall বা data clear করলে এটি এবং
media access grant হারাতে পারে। গুরুত্বপূর্ণ কাজ বাইরে **Save project as…** করুন,
মূল ভিডিও রাখুন। Debug APK signature mismatch হলে backup ছাড়া data মুছবেন না।

## ২. Project খুললে

উপরে project name ও **Projects-এ ফেরার** বোতাম। তার নিচে workspace বিভাগ। Preview,
playhead, timeline এবং selection একই session-এর অংশ; বিভাগ বদলালে নতুন করে editor
বা video surface তৈরি হয় না। ছোট পর্দায় নিচের controls দেখতে scroll করা লাগতে পারে।

| বিভাগ | এই ধাপে যা কাজ করে |
|---|---|
| **Edit** | video import, timeline/playhead, split, trim, move, delete, gap/ripple delete |
| **Media** | import, নির্বাচিত source তথ্য, source relink |
| **Flow** | বর্তমান preview ও export route-এর read-only ব্যাখ্যা; editable nodes নয় |
| **Deliver** | বর্তমান MP4 render, cancel এবং Gallery publication |

Undo/Redo, Save, saved state খোলা, Save As ও explicit recovery সাধারণ project controls।
বর্তমান time ruler/editor grid 30 fps; export-এর source cadence আলাদা। এখনই project
FPS, 4K, HDR বা RAW slider যোগ করা হয়নি, কারণ শুধু UI দিয়ে এগুলো সত্যি চালু হয় না।

## ৩. একটি সাধারণ কাজ

১. Projects → নতুন project → নাম দিন।
২. Edit → ভিডিও নিন → preview প্রস্তুত হলে Play করুন।
৩. লাল playhead/ruler দিয়ে জায়গা বেছে split/trim/move/delete করুন। ভুল হলে Undo।
৪. প্রয়োজন হলে Media-তে source পুনরায় নির্বাচন/relink করুন।
৫. Deliver-এ MP4 render করুন। বর্তমান renderer একটি sequential video track নিয়ে
   720-high SDR H.264/AAC output দেয়; gap বাদ দেওয়ার জন্য আপনার সম্মতি লাগে।
   Android 10+ এ Gallery-র Movies/Owncut-এ যায়; পুরোনো Android-এ file picker থাকে।
৬. **Projects** বা Android **Back** চাপলে পরিবর্তিত local project আগে save হবে,
   তারপর তালিকায় ফিরবে। Save ব্যর্থ হলে editor বন্ধ না হয়ে error দেখাবে।
৭. পরে একই project খুলে saved timeline-এ কাজ চালিয়ে যান। নতুন করে খুললে পুরোনো
   Undo stack ফিরে আসে না; rotation-এ চলমান session/history রাখার ব্যবস্থা আলাদা।

ফোনের Home button/হঠাৎ app বন্ধ/প্রসেস kill স্বাভাবিক Projects-এ ফেরা নয়। Debounced
recovery snapshot থাকতে পারে, কিন্তু শেষ মুহূর্তের প্রতিটি edit রক্ষার নিশ্চয়তা নয়।

## ৪. Recovery কীভাবে

Recovery badge মানে একটি snapshot আছে; সেটি saved primary project-এর সমান বা নতুন—
এমন নিশ্চয়তা নয়। Default-এ saved primary খোলে। দরকার হলে editor-এ **Recover autosave**
চাপুন ও প্রতিস্থাপনের সম্মতি দিন। পছন্দ হলে Save বা Projects দিয়ে local copy-তে রাখুন।
Recovery না নিয়ে নতুন edit করলে আগের recovery snapshot বদলে যেতে পারে। কোনো edit
না করে Projects-এ ফিরলে অযাচিত পুরোনো recovery চুপচাপ মুছে দেওয়া হয় না।

## ৫. আসল node-based editor-এর নকশা

দুটি model একসঙ্গে কাজ করবে:

- **Timeline:** কোন clip কখন চলবে, source-এর কোন অংশ ব্যবহার হবে।
- **Node graph:** সেই image/audio কীভাবে transform, mask, merge, color বা mix হবে।

উদাহরণ (ভবিষ্যৎ processing; এই build-এ এই effect chain চলে না):

```text
Source → সময়/clip range → Transform → Color → Output
                            ↑
                           Mask

দুটি source → Merge → Sequence output
Audio sources → Gain/Mix → Audio output
```

প্রতিটি node-এর typed input/output থাকবে। ভুল ধরনের wire, cycle, missing input ও
অতিরিক্ত resource চাহিদা reject হবে। ইতিমধ্যে C++-এ bounded graph structure যাচাইয়ের
ভিত্তি যোগ হয়েছে; এটি **renderer নয়**, file-এ graph save-ও করে না। বর্তমান preview
MediaPlayer এবং export Media3 আলাদা route-এই চলে। Flow view-তে তাই কোনো ভান করা
editable effect/wire নেই।

পরের ধাপগুলো: session coordinator আলাদা করা → graph format/registry → বাস্তব
source/transform/output evaluator → shared preview/export → compositing/masks/audio →
keyframes/curves/color → proxy/cache ও advanced tools। Custom `.ccraw`-এর জন্য format
specification ও sample দরকার। পূর্ণ পরিকল্পনায় performance, lifecycle, serialization,
color/audio time, cancellation, licensing ও testing gate নির্ধারিত আছে।

## ৬. পরে নকশা বদলাতে হলে

এই নথি ও engineering baseline update করব; বড় সিদ্ধান্তে নতুন ADR রাখব। Saved project
compatibility, output-এর পরিবর্তন এবং migration tests একই সঙ্গে বদলাবে। আগে কাজ করা
import/edit/play/render ভেঙে নতুন page/node যোগ করাকে অগ্রগতি ধরা হবে না।
