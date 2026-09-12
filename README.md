## KZT-LEVELHEAD
**Hypixelネットワークで動作するレベルヘッドMOD。**  
・Forge / DawnClient 1.8.9 向けに作成された最新鋭のレベルヘッドMOD  
・BLC / LCなどにはない機能の搭載  
・完全にカスタマイズ可能な見た目を目指す

### 機能

__Stats取得方法__  
・Hypixel公式API  
・Custom API

__取得可能な情報__  
・Networkレベル  
・Bedwarsスター  
・Skywarsスター  
・UHCレベル   

__追加機能__  
・SeraphとのAPI競合対策
（・ランクの同時表示 例: V, V+, M, M+, ++, NI, YT ）  
（・スターのカラー化 ）  
（・スターブースター ）  
（・カスタムプレフィックス ）  

__API__  
・Seraphとの競合によりAPI Limitの制限を、  
　APIのセルフホストとSeraphの機能「Custom Hypixel Proxy」で実現  
・ポート3015でHypixelAPIの取得結果をそのままポート。
・標準6時間のキャッシュ機能により重複したAPI取得を防止、レート制限を対策。
・ingame中のみの表示でロビーのプレイヤーの不要な取得を防止。

**Dev : Kazut0_@kztmc.net**