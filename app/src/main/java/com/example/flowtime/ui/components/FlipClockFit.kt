package com.example.flowtime.ui.components

import kotlin.math.sqrt

/**
 * 缈荤墝閽?鏁磋鍐呭垏杩涘渾"鐨勫嚑浣曪細**绾暟瀛︼紝涓嶄緷璧?Compose**锛屽彲浠ョ洿鎺ュ崟娴嬨€? *
 * 馃敶 涓轰粈涔堣浠?[FlipClock] 閲屾娊鍑烘潵锛?026-10-25 淇?鍗＄墖鍘嬬幆"鏃跺彂鐜帮紝
 *    杩欏潡鍑犱綍姝ゅ墠鏄啓姝诲湪 Composable 閲岀殑涓夎鍐呰仈绠楀紡锛岃皝涔熸祴涓嶅埌 鈥斺€? *    缁撴灉 4 浣嶆椂琛屽鑳借秴杩囩幆寰勶紙琛?寰?= 1.06锛夈€? 浣嶆椂鍥涜鍒囪繘鎻忚竟 6dp锛? *    鑰屼笖璺ㄨ繃 1 灏忔椂浼氱獊鐒堕噸鎺掋€傚嚑浣曟湰韬病閿欙紝鏄?*娌′汉瀹堜綇瀹?*銆? *    鎶芥垚绾嚱鏁板悗锛孾FlipClockFitTest] 鑳藉湪 JVM 涓婃柇瑷€"浠绘剰鍗℃暟閮戒笉璁稿帇鐜?銆? *
 * 绾﹀畾锛堜笌 [FlipClock] 鐨勬帓鐗堜竴涓€瀵瑰簲锛夛細
 * - 涓€琛?= `slots` 寮犲崱 + `colonCount` 涓啋鍙凤紝Row 鐢?`spacedBy(gap)`锛? *   鎵€浠ラ棿闅旀暟 = `slots + colonCount - 1`锛? * - 鍗″ = 鍗￠珮 脳 `cardAspect`锛堝崱楂樻槸鍞竴鑷敱鍙橀噺锛屽叾浣欓兘鏄浐瀹氶噺锛夛紱
 * - 鍥犳鏁磋瀹藉害鏄崱楂樼殑**涓€娆″嚱鏁?*锛歚row = A路h + B`銆? *
 * 闀垮害鍗曚綅涓€寰嬬敤鍚屼竴绉嶏紙璋冪敤鏂逛紶 dp 灏卞叏绋?dp锛夛紝鏈枃浠朵笉鍏冲績鏄?dp 杩樻槸 px銆? */
internal object FlipClockFit {

    /** 鏁磋瀹藉害锛歚A路h + B`锛孉 = 鍗℃暟 脳 瀹介珮姣旓紝B = 鍐掑彿 + 闂撮殧鐨勫浐瀹氭€诲 */
    fun rowWidth(
        cardHeight: Float,
        slots: Int,
        colonCount: Int,
        cardAspect: Float,
        colonWidth: Float,
        gap: Float,
    ): Float {
        val gaps = (slots + colonCount - 1).coerceAtLeast(0)
        return slots * cardHeight * cardAspect + colonCount * colonWidth + gaps * gap
    }

    /** 鍗＄墖鍥涜鍒板渾蹇冪殑璺濈 = 鏁磋澶栨帴鐭╁舰鐨勫崐瀵硅绾?*/
    fun cornerRadius(
        cardHeight: Float,
        slots: Int,
        colonCount: Int,
        cardAspect: Float,
        colonWidth: Float,
        gap: Float,
    ): Float {
        val halfW = rowWidth(cardHeight, slots, colonCount, cardAspect, colonWidth, gap) / 2f
        val halfH = cardHeight / 2f
        return sqrt(halfW * halfW + halfH * halfH)
    }

    /**
     * 鍒氬ソ鍐呭垏杩涘崐寰?[circleHalfWidth] 鐨勫渾鏃讹紝鍏佽鐨勬渶澶у崱楂樸€?     *
     * 鑱旂珛 `(A路h + B)虏 + h虏 = 4C虏`锛堝洓瑙掕惤鍦ㄥ渾鍛ㄤ笂锛夛紝鍙栨鏍癸細
     * `h = (鈭扐路B + 鈭?4C虏(A虏+1) 鈭?B虏)) / (A虏+1)`銆?     *
     * 鍒ゅ埆寮?鈮?0 琛ㄧず"鍐掑彿+闂撮殧鐨勫浐瀹氬搴﹀氨宸茬粡姣斿渾杩樺"锛岃繑鍥?0
     * 鈥斺€?鐢辫皟鐢ㄦ柟鍏滃簳鍒版渶灏忓崱楂橈紙瀹佸彲鍘嬩竴鐐癸紝涔熶笉鑳借繑鍥炶礋鏁拌鎺掔増宕╂帀锛夈€?     */
    fun maxCardHeightInCircle(
        circleHalfWidth: Float,
        slots: Int,
        colonCount: Int,
        cardAspect: Float,
        colonWidth: Float,
        gap: Float,
    ): Float {
        if (circleHalfWidth <= 0f || slots <= 0) return 0f
        val gaps = (slots + colonCount - 1).coerceAtLeast(0)
        val a = slots * cardAspect
        val b = colonCount * colonWidth + gaps * gap
        val disc = 4f * circleHalfWidth * circleHalfWidth * (a * a + 1f) - b * b
        if (disc <= 0f) return 0f
        return ((-a * b + sqrt(disc)) / (a * a + 1f)).coerceAtLeast(0f)
    }
}


