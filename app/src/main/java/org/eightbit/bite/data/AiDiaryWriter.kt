// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import org.eightbit.bite.core.*
import java.time.ZonedDateTime

const val AI_ESTIMATE_SOURCE="AI estimate"

/** Called only after the user's confirmation, inside a Room transaction. */
suspend fun writeAiEstimate(dao:BiteDao,id:String,mention:AiFoodMention,grams:Double,meal:Int,time:ZonedDateTime,
    isSupplement:Boolean=false,kind:String="OTHER") {
    if(dao.entry(id)!=null) return
    require(grams.isFinite() && grams>0 && grams<=100000)
    val n=mention.nutrition ?: Nutrients()
    val food=Food(id="ai-food:$id",name=mention.name,source=AI_ESTIMATE_SOURCE,kcal=n.kcal,protein=n.protein,
        carbs=n.carbs,fat=n.fat,fiber=n.fiber,sugar=n.sugar,sodium=n.sodium)
    val portion=n.portion(grams)
    writeFoodDiary(dao,food,DiaryEntry(id=id,foodId=food.id,name=food.name,source=AI_ESTIMATE_SOURCE,grams=grams,mealType=meal,
        time=time.toInstant().toEpochMilli(),offsetSeconds=time.offset.totalSeconds,kcal=portion.kcal,protein=portion.protein,
        carbs=portion.carbs,fat=portion.fat,fiber=portion.fiber,sugar=portion.sugar,sodium=portion.sodium,
        isSupplement=isSupplement,supplementKind=if(isSupplement) kind else "OTHER"))
}
