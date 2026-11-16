package org.eightbit.bite.data

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.eightbit.bite.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class AiDiaryWriterTest {
    private lateinit var db:BiteDatabase
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),BiteDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    @Test fun approvalWritesTheModelsEstimateWithoutSelectingOrOverwritingALabel()=runBlocking {
        val label=Food(id="catalogue:cheese",name="Cheese",kcal=999.0,protein=1.0)
        db.dao().saveFood(label)
        val estimate=AiFoodMention("Cheese",150.0,true,"Photo estimate",Nutrients(300.0,20.0,2.0,24.0))
        assertTrue(db.dao().entries().first().isEmpty())
        repeat(2) { db.withTransaction { writeAiEstimate(db.dao(),"ai:answer:0",estimate,150.0,1,ZonedDateTime.now()) } }
        val saved=db.dao().entries().first().single()
        assertEquals(450.0,saved.kcal!!,0.001);assertEquals(30.0,saved.protein!!,0.001)
        assertEquals(AI_ESTIMATE_SOURCE,saved.source);assertNotEquals(label.id,saved.foodId)
        assertEquals(999.0,db.dao().food(label.id)!!.kcal!!,0.001)
    }
    @Test fun unknownNutritionStaysNullAfterApproval()=runBlocking {
        db.withTransaction { writeAiEstimate(db.dao(),"ai:unknown",AiFoodMention("Unknown dish",120.0,true,""),120.0,1,ZonedDateTime.now()) }
        val saved=db.dao().entry("ai:unknown")!!
        assertNull(saved.kcal);assertNull(saved.sugar);assertNull(saved.sodium)
    }
    @Test fun estimatedPowderStillHasOnlyOneMealAndOneSupplementIntake()=runBlocking {
        val mention=AiFoodMention("Protein powder",35.0,true,"",Nutrients(400.0,80.0))
        repeat(2) { db.withTransaction { writeAiEstimate(db.dao(),"ai:whey",mention,35.0,1,ZonedDateTime.now(),true,"PROTEIN_POWDER") } }
        assertEquals(1,db.dao().entries().first().size);assertEquals(1,db.dao().supplementIntakes().first().size)
        assertEquals(28.0,db.dao().entry("ai:whey")!!.protein!!,0.001)
        assertEquals(35.0,db.dao().supplementIntakes().first().single().quantity,0.001)
    }
}
