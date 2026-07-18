mod资产归Mangzai-120所有
这个main分支只是用于手动构建用（因为每次都得改build.gradle太麻烦了，输入法记忆功能又导致不方便故意打错模组名字）（mod代码都是原先的ssca分支的，还没动过）
关于手动构建：1.由于目前ssc没发1.10.0正式版所以得先手动构建一个ssc的构建吧（和第2、3步相同，最好用同时期的构建版，也能帮忙排查是否兼容（前提你能看出来））
然后将ssc1.10.0构建版放在这里（文件具体名称参考vscode选择的这一块）<img width="1920" height="1080" alt="屏幕截图 2026-07-18 194315" src="https://github.com/user-attachments/assets/b6e5b87e-c288-4e7a-8573-d12c7c4a95b7" />
2.打开cmd（windowspowershell也行），然后输入cd(空格)(gradle和源代码那一堆乱七八糟的东西放的目录)切换目录，不然构建不了（毕竟你总不可能把那一大堆东西放在用户文件夹或者system32里）<img width="1115" height="628" alt="屏幕截图 2026-07-18 194450" src="https://github.com/user-attachments/assets/92f17899-403c-4e98-998a-3e819dd7a57d" />
3.把gradle.bat拖入窗口（如果用的不是win11的那个终端的话可能需要手动复制文件地址），然后打空格，输入build，然后就开始构建了，构建开始时如图所示：<img width="1115" height="628" alt="屏幕截图 2026-07-18 195036" src="https://github.com/user-attachments/assets/c045bf33-aeff-423b-a3db-c11158b7cef4" />
如果出现绿色success的字样就是构建成功；如果看到红色的failed字样就是构建失败，这时候就得排查一下构建步骤对不对
注：如果是第一次构建，会先下载gradle-8.x.x-bin.zip，下载速度有时会很慢(慢到fabric模组搞得像构建Architectury API模组似的)，甚至因为下载失败导致构建失败，如果你下了PCL的话复制下载网址用然后用PCL下载一下（注:我没接到广告，我提这一点单纯是pcl下载gradle压缩包比cmd下载快多了），之后再将gradle压缩包拖到对应目录（参考图片，打开对应版本号的目录（包括里面那一个看起来像一堆乱码的东西），然后把里面的两个文件删了，把gradle压缩包放进去（别放错版本））
<img width="1670" height="941" alt="屏幕截图 2026-07-18 200012" src="https://github.com/user-attachments/assets/f0813ff4-57ad-4bb4-982a-fcf75f70aea7" />
<img width="1670" height="941" alt="屏幕截图 2026-07-18 200023" src="https://github.com/user-attachments/assets/b8158659-d774-4400-8bd0-34d62844f82d" />
注：由于ssc1.10.0变动极大，如使用构建版，请确保自己有能排查崩溃原因的能力或者工具（最重要的一点：发关于ssca崩溃的issue一定要发完整日志或者崩溃日志，不要只发图片！发关于ssca崩溃的issue一定要发完整日志或者崩溃日志，不要只发图片！发关于ssca崩溃的issue一定要发完整日志或者崩溃日志，不要只发图片！重要的事情提三次！）
