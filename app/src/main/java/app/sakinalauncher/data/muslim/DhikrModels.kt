package app.sakinalauncher.data.muslim

import java.util.Locale

enum class DhikrPeriod {
    MORNING,
    EVENING,
}

data class DhikrCard(
    val titleId: String,
    val titleEn: String,
    val arabic: String,
    val latin: String,
    val meaningId: String,
    val meaningEn: String,
    val repetitionCount: Int = 1,
) {
    fun title(locale: Locale = Locale.getDefault()): String {
        return if (locale.language == "in" || locale.language == "id") titleId else titleEn
    }

    fun meaning(locale: Locale = Locale.getDefault()): String {
        return if (locale.language == "in" || locale.language == "id") meaningId else meaningEn
    }
}

object DhikrContent {
    fun cardsFor(period: DhikrPeriod): List<DhikrCard> {
        return when (period) {
            DhikrPeriod.MORNING -> sharedCards.take(4) + morningOnlyCards + sharedCards.drop(4)
            DhikrPeriod.EVENING -> sharedCards.take(4) + eveningOnlyCards + sharedCards.drop(4) + eveningClosingCards
        }
    }

    private val sharedCards = listOf(
        DhikrCard(
            titleId = "Ayat Kursi",
            titleEn = "Ayat al-Kursi",
            arabic = "اللَّهُ لَا إِلَـٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ، لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ، لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ، مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ، يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ، وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ، وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ، وَلَا يَئُودُهُ حِفْظُهُمَا، وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            latin = "Allahu la ilaha illa huwal-hayyul-qayyum. La ta'khudzuhu sinatuw wa la naum...",
            meaningId = "Allah Maha Hidup dan terus mengurus makhluk-Nya. Kekuasaan dan penjagaan-Nya meliputi langit dan bumi.",
            meaningEn = "Allah is the Ever-Living Sustainer. His knowledge, authority, and protection encompass the heavens and earth.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Surat Al-Ikhlas",
            titleEn = "Surah al-Ikhlas",
            arabic = "بِسْمِ اللَّهِ الرَّحْمَـٰنِ الرَّحِيمِ\nقُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ",
            latin = "Bismillahir-rahmanir-rahim. Qul huwallahu ahad. Allahus-samad. Lam yalid wa lam yulad. Wa lam yakun lahu kufuwan ahad.",
            meaningId = "Katakanlah: Allah Maha Esa, tempat bergantung segala sesuatu. Tidak beranak, tidak diperanakkan, dan tidak ada yang setara dengan-Nya.",
            meaningEn = "Say: Allah is One, the Eternal Refuge. He neither begets nor is born, and none is comparable to Him.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Surat Al-Falaq",
            titleEn = "Surah al-Falaq",
            arabic = "بِسْمِ اللَّهِ الرَّحْمَـٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِنْ شَرِّ مَا خَلَقَ، وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ، وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ، وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ",
            latin = "Bismillahir-rahmanir-rahim. Qul a'udzu birabbil-falaq. Min sharri ma khalaq...",
            meaningId = "Aku berlindung kepada Rabb waktu subuh dari kejahatan makhluk, gelap malam, sihir, dan kedengkian.",
            meaningEn = "I seek refuge in the Lord of daybreak from created harm, darkness, sorcery, and envy.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Surat An-Nas",
            titleEn = "Surah an-Nas",
            arabic = "بِسْمِ اللَّهِ الرَّحْمَـٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَـٰهِ النَّاسِ، مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ",
            latin = "Bismillahir-rahmanir-rahim. Qul a'udzu birabbin-nas. Malikin-nas. Ilahin-nas...",
            meaningId = "Aku berlindung kepada Rabb, Raja, dan Sesembahan manusia dari bisikan setan yang bersembunyi.",
            meaningEn = "I seek refuge in the Lord, King, and God of mankind from the whispering tempter.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Sayyidul Istighfar",
            titleEn = "Master supplication for forgiveness",
            arabic = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَـٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
            latin = "Allahumma anta rabbi la ilaha illa anta, khalaqtani wa ana 'abduka...",
            meaningId = "Ya Allah, Engkau Rabbku. Aku mengakui nikmat-Mu dan dosaku, maka ampunilah aku.",
            meaningEn = "O Allah, You are my Lord. I acknowledge Your favor and my sin, so forgive me.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Mohon kesehatan",
            titleEn = "Wellbeing in body and senses",
            arabic = "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَـٰهَ إِلَّا أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ وَالْفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ، لَا إِلَـٰهَ إِلَّا أَنْتَ",
            latin = "Allahumma 'afini fi badani. Allahumma 'afini fi sam'i. Allahumma 'afini fi basari...",
            meaningId = "Ya Allah, berilah kesehatan pada tubuh, pendengaran, dan penglihatanku. Lindungilah aku dari kekufuran, kefakiran, dan siksa kubur.",
            meaningEn = "O Allah, grant wellbeing to my body, hearing, and sight. Protect me from disbelief, poverty, and grave punishment.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Mohon keselamatan",
            titleEn = "Safety in this life and the next",
            arabic = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي وَآمِنْ رَوْعَاتِي. اللَّهُمَّ احْفَظْنِي مِنْ بَيْنِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي",
            latin = "Allahumma inni as-alukal-'afwa wal-'afiyata fid-dunya wal-akhirah...",
            meaningId = "Ya Allah, aku memohon ampunan, keselamatan, penjagaan, dan rasa aman dalam agama, dunia, keluarga, dan hartaku.",
            meaningEn = "O Allah, I ask You for forgiveness, wellbeing, protection, and safety in my faith, life, family, and wealth.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Lindung dari diri dan setan",
            titleEn = "Protection from the self and Satan",
            arabic = "اللَّهُمَّ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ، فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لَا إِلَـٰهَ إِلَّا أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطَانِ وَشِرْكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءًا أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ",
            latin = "Allahumma 'alimal-ghaybi wash-shahadah, fatiras-samawati wal-ard...",
            meaningId = "Ya Allah, aku berlindung dari keburukan diriku, setan, dan perbuatan buruk yang menimpa diriku atau muslim lain.",
            meaningEn = "O Allah, protect me from my own harm, Satan, and doing harm to myself or another Muslim.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Dengan nama Allah",
            titleEn = "In the name of Allah",
            arabic = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ، وَهُوَ السَّمِيعُ الْعَلِيمُ",
            latin = "Bismillahilladzi la yadurru ma'asmihi shay-un fil-ardi wa la fis-sama, wa huwas-sami'ul-'alim.",
            meaningId = "Dengan nama Allah; bersama nama-Nya tidak ada sesuatu pun di bumi dan langit yang membahayakan. Dia Maha Mendengar lagi Maha Mengetahui.",
            meaningEn = "In Allah's name; with His name nothing on earth or in heaven can cause harm. He is the All-Hearing, All-Knowing.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Ridha kepada Allah",
            titleEn = "Contentment with Allah",
            arabic = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا",
            latin = "Raditu billahi rabba, wa bil-islami dina, wa bi Muhammadin sallallahu 'alayhi wa sallama nabiyya.",
            meaningId = "Aku ridha Allah sebagai Rabb, Islam sebagai agama, dan Muhammad shallallahu 'alaihi wa sallam sebagai nabi.",
            meaningEn = "I am pleased with Allah as Lord, Islam as religion, and Muhammad, peace be upon him, as Prophet.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Ya Hayyu Ya Qayyum",
            titleEn = "O Ever-Living, O Sustainer",
            arabic = "يَا حَيُّ يَا قَيُّومُ، بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ",
            latin = "Ya hayyu ya qayyum, bi rahmatika astaghith, aslih li sha'ni kullah, wa la takilni ila nafsi tarfata 'ayn.",
            meaningId = "Wahai Yang Maha Hidup dan Maha Berdiri Sendiri, perbaikilah semua urusanku dan jangan serahkan aku kepada diriku sendiri.",
            meaningEn = "O Ever-Living Sustainer, set all my affairs right and do not leave me to myself even for a blink.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Tahlil 10 kali",
            titleEn = "Tahlil ten times",
            arabic = "لَا إِلَـٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ، وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
            latin = "La ilaha illallahu wahdahu la sharika lah, lahul-mulku wa lahul-hamd, wa huwa 'ala kulli shay-in qadir.",
            meaningId = "Tidak ada sesembahan yang benar selain Allah semata. Milik-Nya kerajaan dan pujian, dan Dia Mahakuasa atas segala sesuatu.",
            meaningEn = "None has the right to be worshiped except Allah alone. Sovereignty and praise belong to Him, and He has power over everything.",
            repetitionCount = 10,
        ),
        DhikrCard(
            titleId = "Tahlil 100 kali",
            titleEn = "Tahlil one hundred times",
            arabic = "لَا إِلَـٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ، وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
            latin = "La ilaha illallahu wahdahu la sharika lah, lahul-mulku wa lahul-hamd, wa huwa 'ala kulli shay-in qadir.",
            meaningId = "Dzikir tahlil harian yang menegaskan tauhid, kerajaan, pujian, dan kekuasaan Allah.",
            meaningEn = "A daily tahlil that affirms Allah's oneness, sovereignty, praise, and power.",
            repetitionCount = 100,
        ),
        DhikrCard(
            titleId = "Subhanallahi wa bihamdih",
            titleEn = "Glory and praise be to Allah",
            arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            latin = "Subhanallahi wa bihamdih.",
            meaningId = "Mahasuci Allah dan segala puji bagi-Nya.",
            meaningEn = "Glory and praise be to Allah.",
            repetitionCount = 100,
        ),
        DhikrCard(
            titleId = "Istighfar",
            titleEn = "Seeking forgiveness",
            arabic = "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
            latin = "Astaghfirullaha wa atubu ilayh.",
            meaningId = "Aku memohon ampun kepada Allah dan bertaubat kepada-Nya.",
            meaningEn = "I seek Allah's forgiveness and turn to Him in repentance.",
            repetitionCount = 100,
        ),
    )

    private val morningOnlyCards = listOf(
        DhikrCard(
            titleId = "Pagi: kerajaan milik Allah",
            titleEn = "Morning: sovereignty belongs to Allah",
            arabic = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَـٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ، وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ",
            latin = "Asbahna wa asbahal-mulku lillah, walhamdu lillah...",
            meaningId = "Kami memasuki pagi; kerajaan milik Allah. Ya Rabb, berilah kebaikan hari ini dan lindungilah aku dari keburukannya.",
            meaningEn = "We enter morning; sovereignty belongs to Allah. My Lord, grant today's good and protect me from its harm.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Pagi dengan pertolongan Allah",
            titleEn = "Morning by Allah's help",
            arabic = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ",
            latin = "Allahumma bika asbahna, wa bika amsayna, wa bika nahya, wa bika namut, wa ilaykan-nushur.",
            meaningId = "Ya Allah, dengan pertolongan-Mu kami memasuki pagi dan petang, hidup dan mati, dan kepada-Mu kebangkitan.",
            meaningEn = "O Allah, by Your help we enter morning and evening, live and die, and to You is resurrection.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Pagi di atas fitrah",
            titleEn = "Morning upon the fitrah",
            arabic = "أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفًا مُسْلِمًا، وَمَا كَانَ مِنَ الْمُشْرِكِينَ",
            latin = "Asbahna 'ala fitratil-islam, wa 'ala kalimatil-ikhlas...",
            meaningId = "Kami memasuki pagi di atas fitrah Islam, kalimat ikhlas, agama Nabi Muhammad, dan millah Ibrahim yang lurus.",
            meaningEn = "We enter morning upon Islam's fitrah, sincere testimony, the Prophet's religion, and Abraham's upright way.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Tasbih pagi",
            titleEn = "Morning glorification",
            arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ",
            latin = "Subhanallahi wa bihamdih, 'adada khalqih, wa rida nafsih, wa zinata 'arshih, wa midada kalimatih.",
            meaningId = "Mahasuci Allah dan pujian bagi-Nya sebanyak bilangan makhluk, keridhaan-Nya, berat Arsy-Nya, dan tinta kalimat-Nya.",
            meaningEn = "Glory and praise be to Allah by the number of His creation, His pleasure, the weight of His Throne, and the ink of His words.",
            repetitionCount = 3,
        ),
        DhikrCard(
            titleId = "Ilmu, rezeki, amal",
            titleEn = "Knowledge, provision, deeds",
            arabic = "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا، وَرِزْقًا طَيِّبًا، وَعَمَلًا مُتَقَبَّلًا",
            latin = "Allahumma inni as-aluka 'ilman nafi'a, wa rizqan tayyiba, wa 'amalan mutaqabbala.",
            meaningId = "Ya Allah, aku memohon ilmu yang bermanfaat, rezeki yang baik, dan amal yang diterima.",
            meaningEn = "O Allah, I ask You for beneficial knowledge, good provision, and accepted deeds.",
            repetitionCount = 1,
        ),
    )

    private val eveningOnlyCards = listOf(
        DhikrCard(
            titleId = "Petang: kerajaan milik Allah",
            titleEn = "Evening: sovereignty belongs to Allah",
            arabic = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَـٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ، وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ",
            latin = "Amsayna wa amsal-mulku lillah, walhamdu lillah...",
            meaningId = "Kami memasuki petang; kerajaan milik Allah. Ya Rabb, berilah kebaikan malam ini dan lindungilah aku dari keburukannya.",
            meaningEn = "We enter evening; sovereignty belongs to Allah. My Lord, grant tonight's good and protect me from its harm.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Petang dengan pertolongan Allah",
            titleEn = "Evening by Allah's help",
            arabic = "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ",
            latin = "Allahumma bika amsayna, wa bika asbahna, wa bika nahya, wa bika namut, wa ilaykal-masir.",
            meaningId = "Ya Allah, dengan pertolongan-Mu kami memasuki petang dan pagi, hidup dan mati, dan kepada-Mu tempat kembali.",
            meaningEn = "O Allah, by Your help we enter evening and morning, live and die, and to You is the return.",
            repetitionCount = 1,
        ),
        DhikrCard(
            titleId = "Petang di atas fitrah",
            titleEn = "Evening upon the fitrah",
            arabic = "أَمْسَيْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفًا مُسْلِمًا، وَمَا كَانَ مِنَ الْمُشْرِكِينَ",
            latin = "Amsayna 'ala fitratil-islam, wa 'ala kalimatil-ikhlas...",
            meaningId = "Kami memasuki petang di atas fitrah Islam, kalimat ikhlas, agama Nabi Muhammad, dan millah Ibrahim yang lurus.",
            meaningEn = "We enter evening upon Islam's fitrah, sincere testimony, the Prophet's religion, and Abraham's upright way.",
            repetitionCount = 1,
        ),
    )

    private val eveningClosingCards = listOf(
        DhikrCard(
            titleId = "Perlindungan petang",
            titleEn = "Evening protection",
            arabic = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            latin = "A'udzu bikalimatillahit-tammati min sharri ma khalaq.",
            meaningId = "Aku berlindung dengan kalimat-kalimat Allah yang sempurna dari keburukan makhluk-Nya.",
            meaningEn = "I seek refuge in Allah's perfect words from the harm of what He created.",
            repetitionCount = 3,
        ),
    )
}
