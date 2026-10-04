package com.belagavi.tourism.data.repository

import com.belagavi.tourism.data.model.BusRoute
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.model.Transport
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesRepository @Inject constructor() {

    fun getPlaces(): List<Place> {
        return allPlaces
    }

    fun getPlaceById(id: Int): Place? {
        return allPlaces.find { it.id == id }
    }

    fun getPlacesByCategory(category: String): List<Place> {
        return allPlaces.filter { it.category.equals(category, ignoreCase = true) }
    }

    companion object {
        val allPlaces = listOf(
            Place(
                id = 1,
                name = "Belagavi Fort",
                category = "Fort",
                description = "Belagavi Fort is a historic medieval fortress known for its strong stone ramparts, ancient temples, mosques, and strategic legacy across multiple ruling dynasties.",
                lat = 15.8589,
                lon = 74.5228,
                best_time = "Year-round",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Belagavi",
                how_to_reach = "Located in the heart of Belagavi city. You can take an auto-rickshaw from the CBT or Railway Station, which is barely 2 km away.",
                local_tips = "Carry drinking water. Photography is allowed but be respectful near the religious structures inside.",
                detailed_history = "The Belagavi Fort is a towering chronicle of Indian history, its walls stained with the blood of multiple empires. Originally built with mud and stone in 1204 AD by the Ratta Dynasty, it was massively fortified by the Adil Shahi Sultans of Bijapur in the 16th century, who added the deep moat, massive bastions, and the majestic Safa Masjid. Later, it became a crucial military stronghold for the Maratha Empire, defending against Mughal expansions. In 1818, the British East India Company captured it, turning it into a military cantonment. During the Indian Independence movement, the fort served as a prison where Mahatma Gandhi was briefly incarcerated. Today, the coexistence of Jain Basadis and ancient Mosques inside its walls tells a profound story of Belagavi's syncretic cultural heritage.",
                folder_name = "belagavi_fort",
                history = "Built in 1204 AD by the Ratta Dynasty, later ruled by the Adil Shahis and Marathas.",
                architecture = "Stone masonry with a deep surrounding moat and Persian inscriptions.",
                famous_features = "Ancient Mosques, Jain Temples, and the Ramakrishna Mission Ashram.",
                transport = Transport(
                    distance_from_city = "2 km from Belagavi CBT",
                    auto_taxi = "Auto from CBT: Rs.40-60. Taxi: Rs.150-200.",
                    drive = "Camp Road toward Fort Circle. Free parking at Fort gate.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Fort Road", "Every 15 mins", "10 min", "~Rs.10")
                )
                )
            ),
            Place(
                id = 2,
                name = "Bhimagad Wildlife Sanctuary",
                category = "Wildlife",
                description = "Bhimagad Wildlife Sanctuary is a pristine protected forest in the Western Ghats, famous for its rich biodiversity, lush tropical canopy, and rare endangered bats.",
                lat = 15.5648707,
                lon = 74.3352858,
                best_time = "October to March",
                entry_fee = "Rs. 50",
                visit_duration = "4 Hours",
                city = "Khanapur",
                how_to_reach = "Take a forest-route taxi or private vehicle from Belagavi via Jamboti and Khanapur (approx. 50 km). A forest entry permit is required.",
                local_tips = "Prior permission from the forest office is mandatory. Avoid wandering off the designated safari trails.",
                detailed_history = "Bhimagad Wildlife Sanctuary is one of Karnataka's most ecologically significant protected areas, nestled deep in the Western Ghats near Khanapur. The sanctuary takes its name from the ancient Bhimagad Fort, which crowns a rocky plateau within its borders — a ruin believed to have been a Maratha garrison used by Shivaji's generals. The forest is part of a crucial wildlife corridor connecting the Dandeli-Anshi Tiger Reserve. Tigers, leopards, wild dogs (dholes), giant squirrels, and hornbills inhabit its dense canopy.",
                folder_name = "bhimagad_wildlife_sanctury",
                history = "Named after the Bhimagad Fort; famous for the bar-headed goose and tigers.",
                architecture = "Dense tropical forest and rugged Western Ghats terrain.",
                famous_features = "Wroughton's free-tailed bat (critically endangered).",
                transport = Transport(
                    distance_from_city = "45 km via Khanapur",
                    auto_taxi = "Taxi from Belagavi: Rs.500-700 round trip.",
                    drive = "NH748 to Khanapur, then forest road. 4WD recommended.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Khanapur", "Every 45 mins", "1 hr", "~Rs.40"),
                    BusRoute("Khanapur -> Bhimagad (shared jeep)", "On demand", "30 min", "~Rs.25-30")
                )
                )
            ),
            Place(
                id = 3,
                name = "Ghataprabha Bird Sanctuary",
                category = "Wildlife",
                description = "Ghataprabha Bird Sanctuary is a scenic wetland along the Ghataprabha River, offering safe shelter to thousands of beautiful migratory birds like cranes and storks.",
                lat = 16.2238,
                lon = 74.757,
                best_time = "November to February",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Gokak",
                how_to_reach = "Travel by road or train from Belagavi to Gokak (approx. 65 km), then proceed to the sanctuary located near Ghataprabha town.",
                local_tips = "Visit during the winter months (November to February) to spot migratory birds. Carry binoculars and telephoto lenses.",
                detailed_history = "The Ghataprabha Bird Sanctuary stretches along a 34-km stretch of the Ghataprabha River near Gokak, making it one of Karnataka's most rewarding birdwatching destinations. The river's shallow sandy banks and tall riparian vegetation attract hundreds of migratory species every winter. The sanctuary is particularly celebrated for the dramatic annual arrival of Demoiselle Cranes from Central Asia and European Storks from Scandinavia.",
                folder_name = "bird_sanctuary_gahatprabha",
                history = "Established around the Ghataprabha River stretch for migratory bird protection.",
                architecture = "Riverine forest and wetlands.",
                famous_features = "Demoiselle cranes and European storks.",
                transport = Transport(
                    distance_from_city = "60 km near Gokak",
                    auto_taxi = "Auto from Gokak: Rs.150-200. Taxi from Belagavi: Rs.1200.",
                    drive = "NH67 to Gokak, then Ghataprabha River road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Gokak", "Every 30 mins", "1.5 hrs", "~Rs.60"),
                    BusRoute("Gokak -> Hidkal Junction", "Every 1 hr", "20 min", "~Rs.20")
                )
                )
            ),
            Place(
                id = 4,
                name = "Chorla Ghat",
                category = "Nature",
                description = "Chorla Ghat is a spectacular mountain pass in the Western Ghats, offering lush tropical forests, winding misty roads, and dramatic seasonal waterfalls.",
                lat = 15.6495989,
                lon = 74.1189133,
                best_time = "Monsoon (July-Sept)",
                entry_fee = "Free",
                visit_duration = "3 Hours",
                city = "Jamboti",
                how_to_reach = "Drive along the scenic SH20 highway connecting Belagavi to Goa (approx. 55 km). Regular KSRTC and private Goa-bound buses pass through.",
                local_tips = "Drive slow as the ghat roads have sharp blind curves. Heavy fog is extremely common during monsoons.",
                detailed_history = "Chorla Ghat is a spectacular mountain pass snaking through the tri-junction of Karnataka, Goa, and Maharashtra. Historically, this ghat was a lifeline for trade caravans connecting the coastal ports of Goa with the Deccan Plateau. The pass cuts through the last surviving stretches of the Western Ghats' semi-evergreen and moist deciduous forests.",
                folder_name = "chorla_ghat",
                history = "Strategic pass used historically for trade between Karnataka and Goa.",
                architecture = "Winding mountainous roads through Sahyadri range.",
                famous_features = "Lush green valley views and heavy mist during monsoon.",
                transport = Transport(
                    distance_from_city = "55 km (Goa border)",
                    auto_taxi = "Taxi from Belagavi: Rs.900-1100. Bike rentals recommended.",
                    drive = "SH20 toward Jamboti, then Chorla Ghat Road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Jamboti", "3-4 buses/day", "1.5 hrs", "~Rs.55"),
                    BusRoute("Belagavi -> Panaji (Goa) via Chorla", "2 buses/day", "4 hrs", "~Rs.180")
                )
                )
            ),
            Place(
                id = 5,
                name = "Godchinamalaki Falls, Gokak",
                category = "Waterfall",
                description = "Godchinamalaki Falls is a beautiful two-tiered cascading waterfall formed by the Markandeya River as it tumbles over terraced volcanic rocks.",
                lat = 16.1227793,
                lon = 74.7499895,
                best_time = "August to October",
                entry_fee = "Free",
                visit_duration = "3 Hours",
                city = "Gokak",
                how_to_reach = "Drive 60 km from Belagavi via Gokak town, then follow the local route towards the falls. A short walk from the parking lot is required.",
                local_tips = "The riverbed rocks are exceptionally slippery. Stay away from deep pool boundaries and avoid swimming.",
                detailed_history = "Godchinamalaki Falls is a pristine two-tiered waterfall formed by the Markandeya River as it descends from the Deccan Plateau near Gokak. The valley has been a place of local spiritual significance for over a thousand years, characterized by terraced volcanic basalt flows that form a beautiful natural amphitheater.",
                folder_name = "godachimalaki_falls",
                history = "A hidden natural wonder formed by the Markandeya river.",
                architecture = "Two-step cascading waterfall over rugged rock formations.",
                famous_features = "The quiet, serene environment away from crowds.",
                transport = Transport(
                    distance_from_city = "65 km near Gokak",
                    auto_taxi = "Taxi from Belagavi: Rs.800-950 round trip.",
                    drive = "NH67 to Gokak, then village road to Yargatti.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Gokak", "Every 30 mins", "1.5 hrs", "~Rs.60"),
                    BusRoute("Gokak -> Godchinamalaki (shared auto)", "On demand", "30 min", "~Rs.40")
                )
                )
            ),
            Place(
                id = 6,
                name = "Gokak Falls",
                category = "Waterfall",
                description = "Gokak Falls is a magnificent 170-foot waterfall on the Ghataprabha River, famed for its historic suspension bridge and early hydroelectric project.",
                lat = 16.1917,
                lon = 74.7765,
                best_time = "July to October",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Gokak",
                how_to_reach = "Located 60 km from Belagavi. Easily accessible via state transport buses running to Gokak town, from where local autos are readily available.",
                local_tips = "Walk across the historic hanging bridge for a breathtaking view. Avoid visiting in high summer when water flow is minimal.",
                detailed_history = "Gokak Falls is a thunderous 52-metre (170-foot) horseshoe-shaped plunge of the Ghataprabha River over red laterite cliffs. In 1887, Gokak became the site of Asia's first hydroelectric power project, built to power the adjoining historic textile mill.",
                folder_name = "gokak",
                history = "Known as the Niagara of India; site of Asia's first hydroelectric project.",
                architecture = "Red stone cliff plunge of 170 feet into a horseshoe-shaped valley.",
                famous_features = "201-meter long suspension bridge over the river.",
                transport = Transport(
                    distance_from_city = "60 km from Belagavi",
                    auto_taxi = "Auto from Gokak Bus Stand to falls: Rs.40-60. Taxi from Belagavi: Rs.700-850.",
                    drive = "NH67 to Gokak. Follow Gokak Falls signboards.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Gokak", "Every 30 mins", "1.5 hrs", "~Rs.60")
                )
                )
            ),
            Place(
                id = 7,
                name = "Hidkal Dam",
                category = "Reservoir",
                description = "Hidkal Dam is a massive reservoir built across the Ghataprabha River, known for its scenic backwaters, migratory bird watching, and stunning sunset views.",
                lat = 16.1462048,
                lon = 74.642605,
                best_time = "Post-Monsoon",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Hukkeri",
                how_to_reach = "Drive 50 km north from Belagavi via Hukkeri on the NH48 national highway, then take the state highway detour to the dam.",
                local_tips = "The sunset view from the reservoir dyke is beautiful. Photography near the core spillway area might require security approval.",
                detailed_history = "Hidkal Dam, built across the Ghataprabha River near Hukkeri, is one of the largest irrigation projects in Karnataka, serving the agriculturally rich northern region. Constructed between 1977 and 1985, the reservoir offers a spectacular view of rolling hills and sunsets.",
                folder_name = "hidkal_dam_hukkeri",
                history = "A major irrigation project built across the Ghataprabha river.",
                architecture = "Modern masonry and earthen dam structure.",
                famous_features = "Scenic sunset views and massive water reservoir.",
                transport = Transport(
                    distance_from_city = "50 km (Hukkeri taluk)",
                    auto_taxi = "Auto from Hukkeri: Rs.150-200. Taxi from Belagavi: Rs.650-800.",
                    drive = "SH51 to Hukkeri, then Hidkal Dam Road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Hukkeri", "Every 1 hr", "1 hr", "~Rs.45"),
                    BusRoute("Hukkeri -> Hidkal Dam", "3 buses/day", "25 min", "~Rs.20")
                )
                )
            ),
            Place(
                id = 8,
                name = "Jalavane Falls",
                category = "Waterfall",
                description = "Jalavane Falls is a seasonal cascade hidden deep inside the Mhadei forest corridor, offering a pristine and quiet trekking path through wild nature.",
                lat = 15.6486329,
                lon = 74.1939471,
                best_time = "July to September",
                entry_fee = "Free",
                visit_duration = "4 Hours",
                city = "Khanapur",
                how_to_reach = "Take a bus or vehicle from Belagavi to Khanapur (26 km), then proceed via Hemmadaga forest checkpoint (another 9 km) to reach the trailhead.",
                local_tips = "Obtain forest department permission if required at the checkpoint. Carry leech protection socks and water during monsoon.",
                detailed_history = "Jalavane Falls is a seasonal cascade hidden deep in the Mhadei Wildlife Corridor near the Karnataka-Goa border. The trek passes through highly biodiverse bamboo forest trails, frequented by hornbills and other tropical bird species.",
                folder_name = "jalavane_falls",
                history = "A hidden seasonal waterfall nestled in the dense Mhadei forest corridor.",
                architecture = "A multi-tiered natural cascade flowing over layered step-like rock formations.",
                famous_features = "Pronged jungle trekking route, lush green canopy, and seasonal biodiversity.",
                transport = Transport(
                    distance_from_city = "35 km via Khanapur",
                    auto_taxi = "Auto from Khanapur to checkpoint: Rs.150-200. Taxi from Belagavi: Rs.700-900.",
                    drive = "NH748 to Khanapur, then take the Khanapur-Hemmadaga forest road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Khanapur", "Every 30 mins", "45 min", "~Rs.35")
                )
                )
            ),
            Place(
                id = 9,
                name = "Jamboti Falls",
                category = "Waterfall",
                description = "Jamboti Falls is a popular cascading waterfall nestled in the Sahyadri mountains, surrounded by beautiful vegetation and rocky natural pools.",
                lat = 15.682445,
                lon = 74.3451657,
                best_time = "August to December",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Jamboti",
                how_to_reach = "Take the Belagavi-Goa road via Jamboti (30 km), then hike the short forest pathway from the main road point.",
                local_tips = "Wear sturdy trekking shoes as the trail gets slushy. Avoid visiting during heavy rain downpours.",
                detailed_history = "Jamboti Falls cascades through the Sahyadri mountains near the quiet village of Jamboti. The area sit at a beautiful altitude where rain clouds drift through mountain gaps, feeding the waterfalls and rock pools.",
                folder_name = "jamboti_falls",
                history = "A popular picnic spot originating in the Sahyadri mountains.",
                architecture = "Tumbling white water over green forest terrain.",
                famous_features = "Excellent photography spots and natural rock pools.",
                transport = Transport(
                    distance_from_city = "50 km from Belagavi",
                    auto_taxi = "Auto from Jamboti village: Rs.50-80. Taxi from Belagavi: Rs.700-850.",
                    drive = "SH20 toward Jamboti.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Jamboti", "3-4 buses/day", "1.5 hrs", "~Rs.55")
                )
                )
            ),
            Place(
                id = 10,
                name = "Jamboti Hills",
                category = "Nature",
                description = "Jamboti Hills is a high-altitude green watershed region near the Western Ghats, celebrated as the headwaters of the Mandovi River.",
                lat = 15.698,
                lon = 74.356,
                best_time = "Winter and Monsoon",
                entry_fee = "Free",
                visit_duration = "3 Hours",
                city = "Belagavi",
                how_to_reach = "Located 30 km from Belagavi. Hire a private vehicle or taxi from CBT and take the scenic road towards Jamboti.",
                local_tips = "Great spot for mist photography. Keep hydration handy as there are no shops or eateries near the hilltops.",
                detailed_history = "Jamboti Hills marks the high boundary of the Northern Western Ghats in Belagavi, serving as the strategic geographical watershed where the Mandovi River originates before winding into Goa.",
                folder_name = "jamboti_hills",
                history = "A high-altitude forest region known as the source of Mandovi river.",
                architecture = "Evergreen forest hills with high rainfall records.",
                famous_features = "Mist-covered peaks and rich birdlife.",
                transport = Transport(
                    distance_from_city = "52 km from Belagavi",
                    auto_taxi = "Taxi from Belagavi: Rs.700-900 round trip.",
                    drive = "SH20 via Kangrali to Jamboti Hills.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Jamboti", "3-4 buses/day", "1.5 hrs", "~Rs.55")
                )
                )
            ),
            Place(
                id = 11,
                name = "Kamal Basadi",
                category = "Temple",
                description = "Kamal Basadi is a historic 12th-century Jain temple inside the Belagavi Fort, celebrated for its exquisite Chalukyan architecture and a magnificent 72-petal stone lotus carved on the ceiling.",
                lat = 15.8592,
                lon = 74.5228,
                best_time = "Morning (8AM-11AM)",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Located inside the Belagavi Fort premises. Easily accessible by auto or taxi from anywhere in Belagavi city.",
                local_tips = "Best to visit early morning when the sunlight hits the intricate stone carvings. Photography is allowed but be respectful.",
                detailed_history = "Hidden within the massive walls of the Belagavi Fort lies the Kamal Basadi, a masterpiece of Jain architecture built in 1204 AD. The temple is internationally famous for its breathtaking stone-carved lotus featuring exactly 72 petals representing Jain Tirthankaras.",
                folder_name = "kamalbasadi_belagavi",
                history = "A 12th-century Jain temple built by the Ratta dynasty inside the fort.",
                architecture = "Famous for the 72-petal stone lotus carved on the ceiling.",
                famous_features = "Basalt stone pillars and Chalukyan carvings.",
                transport = Transport(
                    distance_from_city = "2 km (inside Belagavi Fort)",
                    auto_taxi = "Auto from CBT: Rs.40-60. Walk from Fort gate: 5 minutes.",
                    drive = "Camp Road to Fort gate. Enter fort - temple is inside.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Fort Road", "Every 15 mins", "10 min", "~Rs.10")
                )
                )
            ),
            Place(
                id = 12,
                name = "Kapileshwar Temple, Belagavi",
                category = "Temple",
                description = "Kapileshwar Temple is one of Belagavi's oldest Shiva temples, revered as Dakshina Kashi, and known for its peaceful spiritual atmosphere.",
                lat = 15.8519074,
                lon = 74.5159728,
                best_time = "Mahashivratri festival",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Situated in Shahapur, Belagavi. It is located right in the city and can be easily reached by a short auto ride from the CBT.",
                local_tips = "It gets incredibly crowded during Mahashivratri and Shravan month. Visit on a regular weekday for a peaceful darshan.",
                detailed_history = "Revered as 'Dakshina Kashi', Kapileshwar is arguably the most sacred spiritual center in Belagavi. The temple lingam is believed to be Swayambhu (self-manifested) and has been a site of active worship for over a thousand years.",
                folder_name = "kapileshwar_temple_belagavi",
                history = "Considered the Dakshina Kashi; an ancient Shiva temple.",
                architecture = "Traditional North Karnataka Hindu temple style.",
                famous_features = "The sacred pushkarini (pond) and historic shivling.",
                transport = Transport(
                    distance_from_city = "4 km from Belagavi CBT",
                    auto_taxi = "Auto from CBT: Rs.40-70.",
                    drive = "Khade Bazar Road to Shahapur.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Shahapur", "Every 20 mins", "15 min", "~Rs.12")
                )
                )
            ),
            Place(
                id = 13,
                name = "Khanapur Forest",
                category = "Forest",
                description = "Khanapur Forest is a dense deciduous jungle corridor in the Western Ghats, serving as a vital natural passageway for tigers and elephants.",
                lat = 15.6397,
                lon = 74.5087,
                best_time = "September to January",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Khanapur",
                how_to_reach = "Located along the NH748 highway. Easy access via regular buses and trains running between Belagavi and Londa.",
                local_tips = "Stick to designated safari or trekking tracks. Avoid staying inside the forest past sunset.",
                detailed_history = "Khanapur Forest acts as a major ecological buffer zone, connecting the Sahyadri mountains to the Deccan plains. It is part of an important corridor hosting wild bison, deer, and migrating elephant herds.",
                folder_name = "khanapur_forest",
                history = "A vast ecological buffer zone connecting the Sahyadri ranges with the Deccan plains.",
                architecture = "Lush mix of moist deciduous woodlands, bamboo thickets, and forest streams.",
                famous_features = "Jungle camping, rich teakwood plantations, and spotted deer crossings.",
                transport = Transport(
                    distance_from_city = "26 km from Belagavi",
                    auto_taxi = "Auto from Khanapur town: Rs.100. Taxi from Belagavi: Rs.600-800.",
                    drive = "Follow the NH748 highway directly from Belagavi to Khanapur.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Khanapur", "Every 15-20 mins", "40 mins", "~Rs.30")
                )
                )
            ),
            Place(
                id = 14,
                name = "Kittur Fort",
                category = "Fort",
                description = "Kittur Fort is a historic basalt fortress famed as the seat of Rani Chennamma, who led a heroic armed rebellion against British forces in 1824.",
                lat = 15.6012363,
                lon = 74.7914123,
                best_time = "October to March",
                entry_fee = "Rs. 20",
                visit_duration = "2 Hours",
                city = "Kittur",
                how_to_reach = "Located around 50 km from Belagavi city on the Pune-Bengaluru Highway (NH4). State buses frequently ply to Kittur.",
                local_tips = "Visit the archaeological museum inside the fort premises which closes at 5 PM.",
                detailed_history = "Kittur Fort is the historic epicenter of early armed resistance against British colonial rule. In 1824, Rani Chennamma defended her kingdom from this basalt fort, defeating British troops in an initial battle.",
                folder_name = "kittur_fort",
                history = "Home of Rani Chennamma who fought British in 1824.",
                architecture = "Black basalt ruins with Peshwa-Islamic fusion.",
                famous_features = "Rani Chennamma Museum and the historic Durbar hall.",
                transport = Transport(
                    distance_from_city = "50 km on NH4",
                    auto_taxi = "Auto from Kittur Bus Stand to fort: Rs.30. Taxi from Belagavi: Rs.650-800.",
                    drive = "NH4 (Pune-Bengaluru Highway) to Kittur.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Kittur (KSRTC)", "Every 1 hr", "1 hr 15 min", "~Rs.55"),
                    BusRoute("Belagavi -> Dharwad (stops at Kittur)", "Frequent", "1 hr", "~Rs.60")
                )
                )
            ),
            Place(
                id = 15,
                name = "Kopeshwara Temple",
                category = "Temple",
                description = "Kopeshwara Temple is a magnificent 12th-century Shiva temple in Khidrapur, celebrated for its unique open-to-sky hall and intricate carvings.",
                lat = 16.6159561,
                lon = 74.6856504,
                best_time = "Year-round",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Khidrapur",
                how_to_reach = "Located in Khidrapur, situated on the border of Maharashtra and Karnataka, roughly 85 km from Belagavi.",
                local_tips = "Look out for the stunning Swarga Mandap (open-to-sky hall) which is a masterpiece of ancient engineering.",
                detailed_history = "The Kopeshwara Temple in Khidrapur is an architectural wonder of the Shilahara Dynasty, built on the banks of the Krishna River. It is unique among Shiva temples as it features a dedicated open circular hall (Swarga Mandap) and lacks a Nandi statue due to Puranic context.",
                folder_name = "kopeshwara_temple",
                history = "Ancient temple built by Shilahara Kings in the 12th century.",
                architecture = "Bhumija style with a circular open-to-sky hall.",
                famous_features = "Intricate stone elephant friezes at the temple base.",
                transport = Transport(
                    distance_from_city = "85 km (Khidrapur, near Maharashtra border)",
                    auto_taxi = "Taxi from Belagavi: Rs.1100-1400 round trip.",
                    drive = "NH48 to Nipani, then Khidrapur Road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Nipani", "Every 1 hr", "2 hrs", "~Rs.90"),
                    BusRoute("Nipani -> Khidrapur (local)", "3-4/day", "45 min", "~Rs.30")
                )
                )
            ),
            Place(
                id = 16,
                name = "Kote Kere Lake",
                category = "Park",
                description = "Kote Kere Lake is a beautifully developed historic moat surrounding the Belagavi Fort, featuring boating, pathways, and sunset fountain shows.",
                lat = 15.8679,
                lon = 74.5266,
                best_time = "Evening (6PM-8PM)",
                entry_fee = "Rs. 10",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Situated in the middle of Belagavi city, directly adjacent to the fort. Local autos, buses, and taxis are easily available.",
                local_tips = "Perfect for evening family walks. Speedboating and pedal-boating are active during evening hours.",
                detailed_history = "Kote Kere is the historic defensive moat encircling Belagavi Fort. Excavated originally in the 12th century, it has been modernized into a scenic municipal lake park hosting boating, musical fountain shows, and sunset walkways.",
                folder_name = "kote_kere_belagavi",
                history = "A historic moat lake surrounding the Belagavi Fort.",
                architecture = "Developed recreational waterfront with paved tracks.",
                famous_features = "Musical fountain and floating docks.",
                transport = Transport(
                    distance_from_city = "3 km from Belagavi CBT",
                    auto_taxi = "Auto from CBT: Rs.40-70. Evening fountain show at 7 PM.",
                    drive = "Camp Road to Fort Circle.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Fort Circle", "Every 15 mins", "12 min", "~Rs.10")
                )
                )
            ),
            Place(
                id = 17,
                name = "Military Mahadev Temple, Belagavi",
                category = "Temple",
                description = "Military Mahadev Temple is a beautifully landscaped modern shrine built and maintained by the Indian Army, known for its quiet, peaceful gardens.",
                lat = 15.8461,
                lon = 74.5047,
                best_time = "Any evening",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Located in the Camp area of Belagavi. It is easily reachable by city roads.",
                local_tips = "The temple complex includes a mini zoo and a toy train which is fantastic for children. Very clean and well-maintained.",
                detailed_history = "Maintained with precision by the Maratha Light Infantry Regimental Centre, the Military Mahadev Temple is a spiritual center for garrison soldiers and visitors alike, featuring meticulously manicured gardens.",
                folder_name = "military_mahadev_temple_belagavi",
                history = "Temple built and maintained by the Indian Army (MLIRC).",
                architecture = "Modern temple within lush military-maintained gardens.",
                famous_features = "Toy train and exceptionally clean environment.",
                transport = Transport(
                    distance_from_city = "5 km (Camp area)",
                    auto_taxi = "Auto from CBT: Rs.40-60.",
                    drive = "Khanapur Road to Cantonment Camp.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> MLIRC Gate", "Every 30 mins", "15 min", "~Rs.15")
                )
                )
            ),
            Place(
                id = 18,
                name = "Naviltirtha Reservoir",
                category = "Reservoir",
                description = "Naviltirtha Reservoir is a spectacular deep gorge dam built across the Malaprabha River, surrounded by hills that host a peacock sanctuary.",
                lat = 15.82145,
                lon = 75.0967844,
                best_time = "October to February",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Saundatti",
                how_to_reach = "Travel 80 km east from Belagavi via Saundatti town, then follow the road leading directly to the Malaprabha river gorge.",
                local_tips = "Ideal for quiet day picnics. The deep gorge offers incredible panoramic photography opportunities.",
                detailed_history = "Naviltirtha is a high masonry dam built in a spectacular narrow gorge where the Malaprabha River cuts through the hills of Saundatti, forming a beautiful scenic reservoir that supports local peacock sanctuaries.",
                folder_name = "navilutirtha_reservoir",
                history = "A scenic dam built in a narrow gorge in the hills.",
                architecture = "Engineering marvel with high rock cliffs on both sides.",
                famous_features = "A major peacock sanctuary in the surrounding hills.",
                transport = Transport(
                    distance_from_city = "80 km (Saundatti)",
                    auto_taxi = "Auto from Saundatti to dam: Rs.80-100. Taxi from Belagavi: Rs.1600.",
                    drive = "NH67 to Gokak, Saundatti Road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Saundatti", "Every 1 hr", "2 hrs", "~Rs.90")
                )
                )
            ),
            Place(
                id = 19,
                name = "Parasgad Fort",
                category = "Fort",
                description = "Parasgad Fort is a rugged 10th-century hilltop fortress near Saundatti, showcasing irregular stone ramparts and majestic valley overlooks.",
                lat = 15.7453533,
                lon = 75.140674,
                best_time = "Winter mornings",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Saundatti",
                how_to_reach = "Located near Saundatti, roughly 85 km from Belagavi. You can hire a taxi from Saundatti town.",
                local_tips = "The trek to the top is rocky and uneven. Wear sturdy trekking shoes and carry a hat.",
                detailed_history = "Dating back to the 10th century, Parasgad Fort is a massive hilltop fort built of irregular stone blocks. It was later fortified by Chhatrapati Shivaji Maharaj to overlook the rich Saundatti plains.",
                folder_name = "parasgad_fort",
                history = "One of the oldest forts in the district (10th century).",
                architecture = "Built with irregular stone blocks without mortar.",
                famous_features = "View of the Yellamma Gudi temple from the fort peak.",
                transport = Transport(
                    distance_from_city = "85 km (Saundatti)",
                    auto_taxi = "Auto from Saundatti to fort base: Rs.80-120. Taxi from Belagavi: Rs.1600.",
                    drive = "NH67 to Gokak, Saundatti Road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Saundatti", "Every 1 hr", "2 hrs", "~Rs.90")
                )
                )
            ),
            Place(
                id = 20,
                name = "Rakaskop Reservoir",
                category = "Reservoir",
                description = "Rakaskop Reservoir is a scenic drinking-water dam built on the Markandeya River, surrounded by hills and a legendary giant's cave.",
                lat = 15.806534,
                lon = 74.372723,
                best_time = "Post-Monsoon",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Located just 18 km from Belagavi. Take a local bus or private vehicle towards the Rakaskop village road.",
                local_tips = "Check out the scenic hanging bridge nearby. Carry trash back with you to keep the reservoir drinking water clean.",
                detailed_history = "Built on the Markandeya River, Rakaskop Dam is the primary fresh drinking water source for Belagavi. The scenic reservoir is flanked by laterite hills and legendary caves associated with local mythological stories.",
                folder_name = "rakaskop_reservoir",
                history = "Drinking water source for Belagavi; named after a giant.",
                architecture = "Masonry dam surrounded by peaceful forest hills.",
                famous_features = "The Rakshasa Cave located on the nearby hillside.",
                transport = Transport(
                    distance_from_city = "18 km from Belagavi CBT",
                    auto_taxi = "Auto from Belagavi: Rs.150-200. Taxi: Rs.500-600.",
                    drive = "NH748 west from Belagavi, take Rakaskop turnoff.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Rakaskop (Khanapur Road)", "4-5/day", "35 min", "~Rs.25")
                )
                )
            ),
            Place(
                id = 21,
                name = "Ramdurga Fort",
                category = "Fort",
                description = "Ramdurga Fort is a grand Maratha hill fortress with thick stone walls and watchtowers, offering scenic panoramic views over the Ramdurg valley.",
                lat = 15.9480855,
                lon = 75.2918987,
                best_time = "October to February",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Ramdurg",
                how_to_reach = "Located in Ramdurg town, roughly 100 km from Belagavi. Regular buses connect Ramdurg to Belagavi.",
                local_tips = "The fort is massive; allocate at least 2 hours. Local guides are usually available near the entrance.",
                detailed_history = "Ramdurga Fort was a powerful defensive stronghold for the Ramdurg Princely State Bhave rulers. The classic Maratha military architecture features sloping bastions designed to deflect early artillery fire.",
                folder_name = "ramdurga_fort",
                history = "Administrative center for the Ramdurg Princely State.",
                architecture = "Classic Maratha hill fortification with bastions.",
                famous_features = "The scenic view of the Ramdurg valley below.",
                transport = Transport(
                    distance_from_city = "100 km (Ramdurg town)",
                    auto_taxi = "Auto from Ramdurg to fort: Rs.30-50. Taxi from Belagavi: Rs.1300.",
                    drive = "NH67 to Gokak, then SH64 to Ramdurg.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Ramdurg (KSRTC)", "Every 2 hrs", "2.5 hrs", "~Rs.110")
                )
                )
            ),
            Place(
                id = 22,
                name = "Sada Falls",
                category = "Waterfall",
                description = "Sada Falls is a magnificent hidden waterfall deep in the Sahyadri mountains, requiring a scenic trek through rivers, valleys, and forest canopy.",
                lat = 15.6628497,
                lon = 74.1006311,
                best_time = "August to December",
                entry_fee = "Free",
                visit_duration = "4 Hours",
                city = "Khanapur",
                how_to_reach = "Drive from Belagavi towards Jamboti and take the route to Sada village (near the Goa border), from where a 4 km trek begins.",
                local_tips = "The trek is strenuous and involves crossing streams. Start early in the morning and hire a local village guide.",
                detailed_history = "Sada Falls cascades down a dramatic volcanic rock gorge near Sada village. The trek to this hidden waterfall offers scenic paths through mountain streams, bamboo groves, and lush valleys.",
                folder_name = "sada_falls",
                history = "An adventurous volcanic canyon waterfall near the historic Sada village.",
                architecture = "A dramatic single-plunge cascade carved through ancient volcanic rock fissures.",
                famous_features = "Volcanic crawl-through caves, deep rock gorges, and scenic high-altitude trekking.",
                transport = Transport(
                    distance_from_city = "55 km via Jamboti",
                    auto_taxi = "Taxi from Belagavi to Sada village: Rs.1200-1500. Local guides available at the village.",
                    drive = "Take Belagavi-Jamboti Road, proceed towards Kankumbi, and take the detour to Sada village.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Jamboti", "Every 1 hour", "1 hr", "~Rs.40")
                )
                )
            ),
            Place(
                id = 23,
                name = "Siddheshwar Temple, Kanbargi, Belagavi",
                category = "Temple",
                description = "Siddheshwar Temple is a revered hilltop cave shrine near Kanbargi village, offering a serene meditative environment and cool mountain breezes.",
                lat = 15.90444,
                lon = 74.5595034,
                best_time = "Mondays and Shivratri",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Located in Kanabargi, Belagavi. Accessible via local auto or cab.",
                local_tips = "The temple is located near a hillock, offering a nice view and a cool breeze. Best visited in the late afternoon.",
                detailed_history = "Set into a scenic hill cave near Kanbargi, Siddheshwar Temple is dedicated to Lord Shiva. It offers a peaceful meditative environment and hosts local community festivals during holy Hindu calendar months.\n\nSiddheshwar Temple is known among many visitors for the unusual peace it brings. Some places feel silent; this one feels understanding. For people carrying memories, longing, pain, or emotions difficult to express, the calm surroundings often turn reflection into quiet strength.",
                folder_name = "siddheshwar_temple_belagavi",
                history = "A revered local temple dedicated to Lord Shiva.",
                architecture = "Traditional South Indian temple shikhara.",
                famous_features = "Peaceful meditation atmosphere and local festival fairs.",
                transport = Transport(
                    distance_from_city = "8 km from Belagavi CBT",
                    auto_taxi = "Auto from CBT: Rs.60-80.",
                    drive = "Khanapur Road to Kanabargi.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Kanbargi village", "Every 30 mins", "25 min", "~Rs.15")
                )
                )
            ),
            Place(
                id = 24,
                name = "Surala Falls",
                category = "Waterfall",
                description = "Surala Falls is a dramatic seasonal waterfall that plunges hundreds of feet into a lush forested canyon along the misty Goa-Karnataka border.",
                lat = 15.6746107,
                lon = 74.1860141,
                best_time = "July to September",
                entry_fee = "Free",
                visit_duration = "4 Hours",
                city = "Khanapur",
                how_to_reach = "Located on the Belagavi-Goa road via Chorla Ghat. Private vehicles or KSRTC Goa-bound buses are the best options.",
                local_tips = "Visit during peak monsoon (July to September) for the best views. The viewpoint can get extremely windy and misty.",
                detailed_history = "Surala Falls, located in the Sural valley along the Karnataka-Goa border, plunges down a sheer rocky precipice. During the monsoon, the heavy mist and roaring canyon cascade create a highly spectacular sight.",
                folder_name = "surala_falls",
                history = "A breathtaking monsoon cascade in the rugged Sural valley canyon.",
                architecture = "A spectacular sheer plunge falling into a deep, steep V-shaped mist-filled valley.",
                famous_features = "Sural Valley viewpoint, misty mountain landscapes, and wild trekking paths.",
                transport = Transport(
                    distance_from_city = "50 km via Chorla Ghat",
                    auto_taxi = "Taxi from Belagavi: Rs.1100-1400. Highly recommended for a comfortable day trip.",
                    drive = "Follow the SH20 (Belagavi-Jamboti-Chorla road) directly to the Sural Falls viewpoint.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Panaji (via Chorla)", "Several daily KSRTC/Goa Kadamba buses", "1.5 hrs", "~Rs.70")
                )
                )
            ),
            Place(
                id = 25,
                name = "Tilari Forest",
                category = "Forest",
                description = "Tilari Forest is a dense evergreen wildlife corridor, providing critical habitat for migrating elephants, bison, and rare birds.",
                lat = 15.7734187,
                lon = 74.0917556,
                best_time = "September to January",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Belagavi",
                how_to_reach = "Drive from Belagavi towards Shinoli in Maharashtra, then follow the road via Chandgad directly to Tilari Ghat.",
                local_tips = "Spotting wild elephants is common, so avoid trekking deep into the woods alone. The views from the ghat viewpoint are spectacular during sunrise.",
                detailed_history = "Tilari Forest is a protected semi-evergreen wildlife corridor in the Western Ghats. Known for heavy rainfall and dense flora, it serves as an important habitat for elephant migrations.",
                folder_name = "tilari_forest_belagavi",
                history = "A protected semi-evergreen forest reserve along the borders of Karnataka, Maharashtra, and Goa.",
                architecture = "A lush, dense canopy of Western Ghats tropical evergreen and moist deciduous trees.",
                famous_features = "Tilari Ghat overlook, wild elephant corridors, and rich endemic bird species.",
                transport = Transport(
                    distance_from_city = "65 km via Shinoli/Chandgad",
                    auto_taxi = "Taxi from Belagavi: Rs.1500-1800 for a full-day round trip. Highly recommended.",
                    drive = "Take the Belagavi-Shinoli-Chandgad route, then turn south towards Tilarinagar.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Chandgad", "Regular MSRTC/KSRTC buses", "1.5 hrs", "~Rs.60")
                )
                )
            ),
            Place(
                id = 26,
                name = "Vajrashkala Falls",
                category = "Waterfall",
                description = "Vajrashkala Falls is a majestic waterfall in the Western Ghats, formed by the Mandovi River plunging down a sheer 120-foot basalt cliff face.",
                lat = 15.6585625,
                lon = 74.1071875,
                best_time = "July to September",
                entry_fee = "Free",
                visit_duration = "4 Hours",
                city = "Khanapur",
                how_to_reach = "Take a vehicle to Jamboti village (30 km), then proceed towards the forest path leading to the falls trailhead.",
                local_tips = "It is a remote trek without defined paths. Go in groups, carry a compass or offline maps, and avoid visiting in heavy rains due to wild streams.",
                detailed_history = "Vajrashkala Falls is a majestic waterfall formed by the Mandovi River plunging over a high rocky cliff in the Jambu Hills. It is accessible via challenging forest trails popular among wilderness hikers.",
                folder_name = "vajrapoha_falls",
                history = "A majestic, secluded waterfall on the Mandovi River, revered in local folklore.",
                architecture = "A magnificent 200-foot vertical drop over a rugged basalt rock cliff in the Jamboti hills.",
                famous_features = "Stunning view of the Mandovi River origin, bird watching, and wilderness solitude.",
                transport = Transport(
                    distance_from_city = "35 km via Jamboti",
                    auto_taxi = "Taxi from Belagavi: Rs.900-1100. Local jeeps can be hired from Jamboti for the forest trail.",
                    drive = "Take the Belagavi-Jamboti Road, turn towards the Kaneri forest route.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Jamboti", "Every 1 hour", "1 hr", "~Rs.35")
                )
                )
            ),
            Place(
                id = 27,
                name = "Vidhana Soudha",
                category = "Building",
                description = "Suvarna Vidhana Soudha is a magnificent state assembly building in Belagavi, showcasing grandiose Greco-Roman and Dravidian stone architecture.",
                lat = 15.8133,
                lon = 74.5703,
                best_time = "Year-round",
                entry_fee = "Free",
                visit_duration = "1 Hour",
                city = "Belagavi",
                how_to_reach = "Located right on the Pune-Bengaluru Highway. Easily accessible by local auto or taxi.",
                local_tips = "Photography is allowed from the outside. Best viewed in the evening when illuminated.",
                detailed_history = "Inaugurated in 2012, Suvarna Vidhana Soudha stands as a monument to administrative decentralization. The massive neo-Dravidian structure is used for annual winter assembly sessions of the state legislature.",
                folder_name = "vidhansoudha_belagavi",
                history = "Suvarna Vidhana Soudha is the legislative assembly building of Karnataka in Belagavi.",
                architecture = "Neo-Dravidian architecture.",
                famous_features = "Grandiose dome and extensive stone carving.",
                transport = Transport(
                    distance_from_city = "8 km from Belagavi CBT",
                    auto_taxi = "Auto from CBT: Rs.100-150.",
                    drive = "Pune-Bengaluru Highway, take Cantonment exit.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Vidhana Soudha", "Every 20 mins", "15 min", "~Rs.15")
                )
                )
            ),
            Place(
                id = 28,
                name = "Yellamma Devi Temple",
                category = "Temple",
                description = "Yellamma Devi Temple is a highly sacred hilltop pilgrimage center in Saundatti, dedicated to Goddess Renuka and rich in Puranic legends.",
                lat = 15.7544821,
                lon = 75.1540465,
                best_time = "November to February",
                entry_fee = "Free",
                visit_duration = "3 Hours",
                city = "Saundatti",
                how_to_reach = "Located in Saundatti, about 80 km from Belagavi. Regular KSRTC buses run from Belagavi to Saundatti.",
                local_tips = "Avoid visiting during the massive Banada Hunnime festival unless you are prepared for extreme crowds.",
                detailed_history = "The Yellamma Devi Temple, situated atop the scenic Yellammagudda hill in Saundatti, is the focal point of deeply worshipped Puranic legends. Dedicated to Goddess Renuka-Yellamma, it draws millions of pilgrims from Karnataka, Goa, and Maharashtra annually.",
                folder_name = "yellamadevi_temple_savandati",
                history = "Major pilgrimage site for Goddess Renuka Yellamma.",
                architecture = "Built with Rashtrakuta and Chalukyan influences.",
                famous_features = "Annual Banada Hunnime and the Yellamma Gudi fair.",
                transport = Transport(
                    distance_from_city = "80 km (Saundatti)",
                    auto_taxi = "Auto from Saundatti: Rs.60-100. Taxi from Belagavi: Rs.1600.",
                    drive = "NH67 to Gokak, then Saundatti Road.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Saundatti (KSRTC)", "Every 1 hr", "2 hrs", "~Rs.90"),
                    BusRoute("Saundatti -> Yellamma Gudi (temple bus)", "Every 30 mins", "20 min", "~Rs.15")
                )
                )
            ),
            Place(
                id = 29,
                name = "Yellur Fort",
                category = "Fort",
                description = "Yellur Fort, also known as Rajhansgad, is a stunning hilltop fortress offering a 360-degree panoramic overlook of the countryside.",
                lat = 15.7570903,
                lon = 74.5264991,
                best_time = "Evening (4PM-6:30PM)",
                entry_fee = "Free",
                visit_duration = "2 Hours",
                city = "Belagavi",
                how_to_reach = "Located roughly 15 km from Belagavi city. Best reached by private car or bike as the final stretch involves a steep hill climb.",
                local_tips = "Visit during the late afternoon. The sunset view from the top is breathtaking, but the road back down lacks streetlights.",
                detailed_history = "Towering over the valley on a high peak is the Yellur Fort, historically known as Rajhansgad. Rebuilt by successive dynasties, it served as a Maratha watchtower garrison to monitor military movements, featuring a legendary sweet-water well at the summit.",
                folder_name = "yellur_fort",
                history = "Ancient hill fort also called Rajhansgad.",
                architecture = "High stone ramparts with watchtowers.",
                famous_features = "360-degree sunset view over Belagavi city.",
                transport = Transport(
                    distance_from_city = "15 km from Belagavi CBT",
                    auto_taxi = "Auto from Belagavi: Rs.150-200. Taxi: Rs.500-700.",
                    drive = "Hubli Road, left turn at Yellur signboard.",
                    bus = listOf(
                    BusRoute("Belagavi CBT -> Yellur village", "3-4 buses/day", "35 min", "~Rs.20")
                )
                )
            ), // end of Yellur Fort entry
                // New temple entries added for consistency with web dataset
                Place(
                    id = 30,
                    name = "Shankarling Temple",
                    category = "Temple",
                    description = "Shankarling Temple is a historic shrine in Sankeshwar, revered for its river-side sanctum and rich Shankaracharya heritage.",
                    lat = 16.253262,
                    lon = 74.4785572,
                    best_time = "Morning (8 AM-11 AM)",
                    entry_fee = "Free",
                    visit_duration = "1 Hour",
                    city = "Sankeshwar",
                    how_to_reach = "Located on the banks of the Hiranyakeshi River in Sankeshwar. Reach via Belagavi CBT -> Sankeshwar (NH48) – approx. 30 km.",
                    local_tips = "Visit early to enjoy the river ambience; avoid monsoon when paths become slippery.",
                    detailed_history = "The Shankarling Math was founded in the 12th century by the great Shankaracharya of the tradition, serving as a centre of Vedic learning and pilgrimage. The deity is said to have been installed by a wandering sage after he witnessed a divine vision at this spot.",
                    folder_name = "shankarling_temple_sankeshwar",
                    history = "Shankarling Math, a revered shrine of the Shankaracharya lineage, was established in Sankeshwar. The deity Shankarling is believed to have manifested here centuries ago, drawn by the sanctity of the Hiranyakeshi River banks.",
                    architecture = "Built in traditional North-Karnataka style with a sanctum surrounded by a pillared mandapa and a shikhara adorned with intricate stone carvings.",
                    famous_features = "Peaceful riverfront setting, ancient stone inscription of the Shankaracharya, annual Mahashivratri fair.",
                    transport = Transport(
                        distance_from_city = "30 km from Belagavi CBT",
                        auto_taxi = "Auto from CBT: Rs.70-100. Taxi: Rs.400-500.",
                        drive = "Take NH48 north to Sankeshwar, turn left at Hiranyakeshi bridge.",
                        bus = listOf(
                            BusRoute("Belagavi CBT -> Sankeshwar", "Every 30 mins", "45 min", "~Rs.20")
                        )
                    )
                ),
                Place(
                    id = 31,
                    name = "Huliyamma Devi Temple",
                    category = "Temple",
                    description = "Huliyamma Devi Temple is a cherished local shrine in Hukkeri, known for its vibrant festivals and community devotion.",
                    lat = 16.1876997,
                    lon = 74.575084,
                    best_time = "Evening (5 PM-8 PM)",
                    entry_fee = "Free",
                    visit_duration = "1 Hour",
                    city = "Hukkeri",
                    how_to_reach = "Located on the main road of Hukkeri town. Reach via Belagavi CBT -> Hukkeri (NH48) – about 18 km.",
                    local_tips = "Best visited during the evening festival; parking available near the temple entrance.",
                    detailed_history = "The shrine dates back to the 17th century when local villagers erected a simple shrine after a reported miracle. Over generations it grew into a beloved community temple, hosting the annual Huliyamma fair attracting pilgrims from nearby villages.\n\nMany devotees believe Huliyamma Devi Temple is a place where emotions become quieter. The peaceful atmosphere of Hukkeri and the deep faith around the goddess often give visitors a strange sense of calm — especially to those carrying memories, unanswered feelings, or emotional weight in their hearts.",
                    folder_name = "huliyamma_devi_temple",
                    history = "Huliyamma Devi Temple is a local goddess shrine situated in Hukkeri, Belagavi district. Devotees believe Huliyamma appeared here to protect the surrounding villages.",
                    architecture = "Simple yet charming stone structure with a thatched roof, featuring a wooden sanctum and a surrounding courtyard used for festivals.",
                    famous_features = "Annual Huliyamma Jatre, nearby sacred banyan tree, traditional folk music performances.",
                    transport = Transport(
                        distance_from_city = "18 km from Belagavi CBT",
                        auto_taxi = "Auto from CBT: Rs.50-80. Taxi: Rs.250-300.",
                        drive = "Take NH48 north to Hukkeri; the temple is visible from the main road.",
                        bus = listOf(
                            BusRoute("Belagavi CBT -> Hukkeri", "Every 20 mins", "30 min", "~Rs.15")
                        )
                    )
                )
            )
    }
}
