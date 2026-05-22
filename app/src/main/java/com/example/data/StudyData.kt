package com.example.data

import java.io.Serializable

data class PharmaCourse(
    val id: String,
    val name: String,
    val type: CourseType, // BPHARM, DPHARM
    val semesters: List<PharmaSemester>
)

enum class CourseType {
    BPHARM, DPHARM
}

data class PharmaSemester(
    val id: String,
    val name: String,
    val num: Int,
    val subjects: List<PharmaSubject>
)

data class PharmaSubject(
    val id: String,
    val name: String,
    val code: String,
    val description: String,
    val syllabus: List<SyllabusUnit>,
    val notes: List<PharmaNote>,
    val videos: List<PharmaVideo>,
    val mcqs: List<PharmaMcq>
)

data class SyllabusUnit(
    val title: String, // e.g. "Unit I"
    val description: String, // e.g. "Introduction to dosage forms, prescription, calculation"
    val topics: List<String> // list of detailed subtopics
)

data class PharmaNote(
    val id: String,
    val title: String,
    val chapter: String,
    val content: String, // Markdowns or high-yield bullet notes
    val durationMin: Int = 10
)

data class PharmaVideo(
    val id: String,
    val title: String,
    val tutor: String = "Carewell Pharma",
    val duration: String,
    val youtubeId: String, // Mock / real video ID
    val description: String
)

data class PharmaMcq(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String
)

object StudyData {
    val courses: List<PharmaCourse> = listOf(
        PharmaCourse(
            id = "bpharm",
            name = "Bachelor of Pharmacy (B.Pharm)",
            type = CourseType.BPHARM,
            semesters = listOf(
                PharmaSemester(
                    id = "sem_1",
                    name = "Semester 1",
                    num = 1,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_101",
                            name = "Human Anatomy & Physiology I",
                            code = "BP101T",
                            description = "Introduction to human body, skeletal system, joints, body fluids, lymphatic system and peripheral nervous system.",
                            syllabus = listOf(
                                SyllabusUnit(
                                    "Unit I",
                                    "Introduction to Human Body & Cellular level of Organization",
                                    listOf("Scope of Anatomy and Physiology", "Cell organelles and their functions", "Cell division (Mitosis & Meiosis)", "Elementary tissues of human body")
                                ),
                                SyllabusUnit(
                                    "Unit II",
                                    "Skeletal System & Joints",
                                    listOf("Structure, composition and functions of skeleton", "Classification of joints", "Types of joint movements", "Disorders of joints (Arthritis, Gout)")
                                )
                            ),
                            notes = listOf(
                                PharmaNote(
                                    "hap_n1", "Scope of Anatomy and Physiology", "Introduction",
                                    "**Anatomy:** Science of structure of the human body and cellular organization.\n\n**Physiology:** Science of mechanical, physical, and biochemical functions of humans.\n\n**Levels of Structural Organization:**\n1. Chemical Level (Atoms & Molecules)\n2. Cellular Level (Organelles & Proteins)\n3. Tissue Level (Epithelial, Connective, Muscle, Nervous)\n4. Organ Level (Stomach, Liver, Lung)\n5. System Level (Cardiovascular, Renal, Digestive)\n6. Organismal Level (The whole human body)\n\n**Homeostasis:** State of dynamic balance or stable internal environment maintained by self-regulating feedback loops.",
                                    12
                                )
                            ),
                            videos = listOf(
                                PharmaVideo("hap_v1", "Scope of Anatomy & Physiology - Lecture 1", "Sir Jitendra Patel", "12:45", "E3rs1KbGoJA", "Detailed introduction to anatomy and human organ systems."),
                                PharmaVideo("hap_v2", "Cell Structure & Cell Organelles - Easy Explanation", "Carewell Pharma Team", "22:15", "x8x5fGgS", "Learn about cell wall, plasma membrane, and mitochondria functions.")
                            ),
                            mcqs = listOf(
                                PharmaMcq("hap_q1", "Which cell organelle is known as the powerhouse of the cell?", listOf("Nucleus", "Ribosome", "Mitochondria", "Lysosome"), 2, "Mitochondria produces ATP through cellular respiration, making it the cell powerhouse."),
                                PharmaMcq("hap_q2", "What is homeostasis?", listOf("Cell division speed", "Constant internal balance", "Blood clotting factor", "Joint movement limit"), 1, "Homeostasis refers to the maintenance of static or constant states in the internal environment.")
                            )
                        ),
                        PharmaSubject(
                            id = "bph_102",
                            name = "Pharmaceutical Analysis I",
                            code = "BP102T",
                            description = "Concepts of volumetric and electrochemical analysis, limit tests, theories of indicators and titration methods.",
                            syllabus = listOf(
                                SyllabusUnit("Unit I", "Pharmaceutical Analysis Fundamentals", listOf("Different techniques of analysis", "Primary and secondary standards", "Preparation & standardization of solutions", "Sources of errors")),
                                SyllabusUnit("Unit II", "Acid Base and Non-Aqueous Titrations", listOf("Acid-base theories", "Law of mass action", "Henderson-Hasselbalch equation", "Solvents used in non-aqueous titrations"))
                            ),
                            notes = listOf(
                                PharmaNote(
                                    "pa_n1", "Primary vs Secondary Standards", "Analysis Basics",
                                    "**Primary Standard:** A highly purified compound that serves as a reference material in volumetric titrations.\n- High purity (>99.9%)\n- High stability (does not react with atmosphere, not hygroscopic)\n- High equivalent weight to minimize weighing errors\n- Examples: Sodium Carbonate (Na2CO3), Potassium Hydrogen Phthalate (KHP), Oxalic Acid.\n\n**Secondary Standard:** A substance whose active concentration has been determined by standardization against a primary standard.\n- Less stable, hygroscopic, or volatile.\n- Examples: Sodium Hydroxide (NaOH), Hydrochloric Acid (HCl), Potassium Permanganate (KMnO4).",
                                    15
                                )
                            ),
                            videos = listOf(
                                PharmaVideo("pa_v1", "Primary & Secondary Standards - Solved", "Prof. K. Mishra", "15:10", "3Ers1KbGdJs", "Excellent breakdown of standard materials with titration demo."),
                                PharmaVideo("pa_v2", "Limit Test for Arsenic - Gutzeit apparatus technique", "Carewell Lab Guide", "18:40", "GutAsPk89X", "Visual apparatus walkthrough for B.Pharmacy practicals.")
                            ),
                            mcqs = listOf(
                                PharmaMcq("pa_q1", "Which of the following is used in the setup for the Limit Test of Arsenic?", listOf("Nessler's cylinder", "Gutzeit apparatus", "Pipette", "Pycnometer"), 1, "The Gutzeit apparatus is explicitly designed for the visual limit test of Arsenic where arsine gas forms a yellow stain on mercuric chloride paper."),
                                PharmaMcq("pa_q2", "Why is sodium hydroxide considered a secondary standard?", listOf("It is highly poisonous", "It is extremely cheap", "It absorbs moisture and carbon dioxide from the air", "It has high formula weight"), 2, "Hygroscopic substances absorb atmosphere moisture and impurities, so their concentration changes quickly and they must be standardized.")
                            )
                        ),
                        PharmaSubject(
                            id = "bph_103",
                            name = "Pharmaceutics I",
                            code = "BP103T",
                            description = "Introduction to history of pharmacy, pharmacopoeias, prescription calculation, powders, monophasic & biphasic liquids.",
                            syllabus = listOf(
                                SyllabusUnit("Unit I", "History of Pharmacy & Dosage Forms", listOf("Indian Pharmacopoeia (IP) history", "Introduction to dosage forms", "Prescription reading and parts")),
                                SyllabusUnit("Unit II", "Pharmaceutical Calculations & Powders", listOf("Posology: pediatric dose calculations", "Allegation method", "Classification of Powders", "Eutectic mixtures"))
                            ),
                            notes = listOf(
                                PharmaNote(
                                    "p_n1", "Pediatric Dose Calculations (Posology)", "Calculation Basics",
                                    "**Posology:** Branch of pharmacology dealing with the study of doses.\n\n**Factors Influencing Dose:** Age, sex, body weight, route of administration, drug interactions, disease status.\n\n**Standard Pediatric Formulae:**\n\n1. **Young's Rule** (For child age 1 - 12 years):\n   Child Dose = [Age in Years / (Age in Years + 12)] * Adult Dose\n\n2. **Dilling's Rule** (Better for age 4 - 20 years):\n   Child Dose = [Age in Years / 20] * Adult Dose\n\n3. **Fried's Rule** (For infants to 2 years):\n   Infant Dose = [Age in Months / 150] * Adult Dose\n\n4. **Clark's Rule** (Based on body weight in pounds):\n   Child Dose = [Weight in lbs / 150] * Adult Dose",
                                    18
                                )
                            ),
                            videos = listOf(
                                PharmaVideo("p_v1", "Pediatric Dose Calculations Explained with examples", "Carewell Calculation", "19:30", "PosCal119Z", "Learn Young's, Fried's, and Clark's rule formulations step-by-step."),
                                PharmaVideo("p_v2", "Monophasic vs Biphasic liquid dosage forms", "Jitendra Patel", "24:10", "MpBpD12Kz", "Learn the essential differences between Syrups, Elixirs, Emulsions, and Suspensions.")
                            ),
                            mcqs = listOf(
                                PharmaMcq("p_q1", "Which of the following formula is based strictly on child's age in months?", listOf("Young's rule", "Fried's rule", "Dilling's rule", "Clark's rule"), 1, "Fried's rule is specifically based on age in months (Age/150 * Adult Dose) for infants."),
                                PharmaMcq("p_q2", "A mixture of ingredients that liquefy when mixed together at room temperature is called:", listOf("Effervescent powder", "Hygroscopic powder", "Eutectic mixture", "Deliquescent mixture"), 2, "Eutectic mixtures lower each other's melting points when combined, causing them to liquefy.")
                            )
                        ),
                        PharmaSubject(
                            id = "bph_104",
                            name = "Pharmaceutical Inorganic Chemistry",
                            code = "BP104T",
                            description = "Study of impurities in pharmaceuticals, limit tests, gastrointestinal agents, topical agents, dental products, and major electrolytes.",
                            syllabus = listOf(
                                SyllabusUnit(
                                    "Unit I",
                                    "Impurities & Limit Tests",
                                    listOf("Sources and effects of impurities in pharmacopoeial substances", "History of Pharmacopoeia", "Limit test for Chloride & Sulfate", "Limit test for Iron, Heavy metals & Arsenic")
                                ),
                                SyllabusUnit(
                                    "Unit II",
                                    "Electrolytes & Dental Products",
                                    listOf("Major extra and intracellular electrolytes", "Physiological role of Sodium, Potassium, Calcium & Chloride", "Dental products: Dentifrices, Desensitizers & Fluorides")
                                )
                            ),
                            notes = listOf(
                                PharmaNote(
                                    "pic_n1", "Limit Test for Chloride and Sulfate", "Impurities & Limit Tests",
                                    "**Limit Test:** Quantitative or semi-quantitative test designed to identify and control small quantities of impurity which are likely to be present in the substance.\n\n**Limit Test for Chloride:**\n- **Principle:** Based on the chemical reaction of soluble chloride with silver nitrate (AgNO3) in the presence of dilute nitric acid (HNO3) to form a silver chloride precipitate which appears as opalescence (turbidity).\n- **Chemical Reaction:** Cl- + AgNO3 --(HNO3)--> AgCl (precipitate) + NO3-\n- **Role of Dilute Nitric Acid:** Prevents precipitation of other acid radicals (like silver phosphate, carbonate) and provides an acidic medium.\n\n**Limit Test for Sulfate:**\n- **Principle:** Based on the precipitation of sulfate with barium chloride (BaCl2) in the presence of dilute hydrochloric acid (HCl) to form insoluble barium sulfate (BaSO4) as turbidity.\n- **Chemical Reaction:** SO4(2-) + BaCl2 --(HCl)--> BaSO4 (precipitate) + 2Cl-\n- **Barium Sulfate Reagent:** Contains barium chloride, sulfate-free alcohol (prevents supersaturation and gives uniform opalescence), and a small amount of potassium sulfate (increases sensitivity of test).",
                                    15
                                )
                            ),
                            videos = listOf(
                                PharmaVideo("pic_v1", "Limit Test for Chloride & Sulfate - Solved", "Carewell Pharma Team", "14:20", "ClLim18Xp", "Learn the complete step-by-step chemical reaction and calculation for Chloride limit tests."),
                                PharmaVideo("pic_v2", "Antacids & Gastrointestinal Agents Lecture", "Carewell Pharma Team", "18:55", "AntGI88Za", "Comprehensive study on Aluminium Hydroxide gel and Antacid classification.")
                            ),
                            mcqs = listOf(
                                PharmaMcq("pic_q1", "Which acid is used in the limit test for Iron to prevent the precipitation of iron by ammonia?", listOf("Citric acid", "Hydrochloric acid", "Nitric acid", "Acetic acid"), 0, "Citric acid forms a soluble complex with iron in ammoniacal solution, preventing iron from precipitating as ferric hydroxide."),
                                PharmaMcq("pic_q2", "What role does alcohol play in the limit test for Sulfate?", listOf("Prevents supersaturation and provides uniform turbidity", "Provides acidic medium for precipitation", "Acts as a catalyst", "Dissolves secondary standard"), 0, "Sulfate-free alcohol prevents supersaturation, which helps in the formation of a homogeneous, uniform, and stable turbidity of BaSO4.")
                            )
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_2",
                    name = "Semester 2",
                    num = 2,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_201",
                            name = "Human Anatomy & Physiology II",
                            code = "BP201T",
                            description = "Nervous system, digestive system, respiratory system, urinary system and endocrine system physiology.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "bph_202",
                            name = "Pharmaceutical Organic Chemistry I",
                            code = "BP202T",
                            description = "Classification, nomenclature, isomerism, and chemical properties of alkanes, alkenes, conjugated dienes, alkyl halides, and alcohols.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "bph_203",
                            name = "Biochemistry",
                            code = "BP203T",
                            description = "Biomolecules, metabolism of carbohydrates, lipids, amino acids, and proteins, nucleic acid metabolism, enzyme kinetics.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_3",
                    name = "Semester 3",
                    num = 3,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_301",
                            name = "Pharmaceutical Organic Chemistry II",
                            code = "BP301T",
                            description = "Benzene structure, orientation, aromatic amines, phenols, fats & oils, polynuclear hydrocarbons.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "bph_302",
                            name = "Physical Pharmaceutics I",
                            code = "BP302T",
                            description = "States of matter, solubility of drugs, surface and interfacial tension, complexation and protein binding.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "bph_303",
                            name = "Pharmaceutical Microbiology",
                            code = "BP303T",
                            description = "Study of bacteria, fungi, virus, sterilization methods, disinfectant evaluations, and sterile area designing.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_4",
                    name = "Semester 4",
                    num = 4,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_401",
                            name = "Pharmacology I",
                            code = "BP404T",
                            description = "General pharmacology, pharmacokinetics, pharmacodynamics, drug receptor interactions, and drugs acting on peripheral & central nervous systems.",
                            syllabus = listOf(
                                SyllabusUnit("Unit I", "General Pharmacology & Pharmacokinetics", listOf("Nomenclature, sources of drugs, routes of administration", "Absorption, Distribution, Metabolism, Excretion (ADME)", "Half-life (t1/2) and bio-availability")),
                                SyllabusUnit("Unit II", "Pharmacodynamics & Receptors", listOf("Mechanism of drug action", "Receptor superfamilies", "G-protein coupled receptors (GPCR)", "Dose-response relationships"))
                            ),
                            notes = listOf(
                                PharmaNote(
                                    "ph_n1", "G-Protein Coupled Receptors (GPCR)", "Receptors",
                                    "**GPCR Superfamily:** The largest family of cell surface receptors responding to hormones and neurotransmitters.\n- Also called **7-Transmembrane Receptors** because they span the plasma membrane 7 times.\n\n**Components of GPCR signaling:**\n1. **Receptor:** Binds the primary ligand.\n2. **G-Protein:** A trimeric protein (alpha, beta, gamma subunits) coupled to the receptor. It binds GDP when inactive and GTP when active.\n3. **Effector Enzyme:** Adenylyl cyclase (produces cAMP) or Phospholipase C (produces IP3 and DAG).\n\n**Activation mechanism:**\n1. Ligand binding changes receptor conformation.\n2. Conformation change releases GDP from alpha-subunit, replacing it with GTP.\n3. G-protein dissociates into active G-alpha-GTP and G-beta-gamma complexes.\n4. Effector enzymes are activated, initiating second messenger cascades (e.g., Protein Kinase A phosphorylation).",
                                    25
                                )
                            ),
                            videos = listOf(
                                PharmaVideo("ph_v1", "GPCR Signaling Pathway - Simplest Drawing", "Jitendra Patel", "21:00", "GpcrRc410", "Animated drawing of G-protein pathways with cyclic AMP, IP3/DAG cycles."),
                                PharmaVideo("ph_v2", "ADME Basics: Absorption & Distribution", "Carewell Pharmacology", "19:25", "AdmePh01S", "Everything about passive diffusion, active transport, and plasma protein binding.")
                            ),
                            mcqs = listOf(
                                PharmaMcq("ph_q1", "How many times does a GPCR span the plasma membrane?", listOf("3 times", "5 times", "7 times", "12 times"), 2, "GPCRs are 7-transmembrane structures, meaning they weave through the bi-lipid membrane 7 times."),
                                PharmaMcq("ph_q2", "Identify the primary second messenger produced by the activation of Adenylyl Cyclase.", listOf("IP3", "cAMP", "Diacylglycerol (DAG)", "Sodium ions"), 1, "Adenylyl Cyclase converts ATP into cyclic AMP (cAMP) as a critical secondary messenger to activate Protein Kinase A.")
                            )
                        ),
                        PharmaSubject(
                            id = "bph_402",
                            name = "Pharmacognosy I",
                            code = "BP405T",
                            description = "Introduction to pharmacognosy, classification of crude drugs, extraction methods, and primary metabolism principles.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_5",
                    name = "Semester 5",
                    num = 5,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_501",
                            name = "Medicinal Chemistry II",
                            code = "BP501T",
                            description = "Anti-anginal, anti-hypertensive, anti-arrhythmic, diuretics, local anaesthetics, antidiabetics, thyroid hormones structure activity relationships.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "bph_502",
                            name = "Industrial Pharmacy I",
                            code = "BP502T",
                            description = "Pre-formulation studies, Tablets, Liquid Orals, Capsules, Parenteral products, and Cosmetic preparations.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_6",
                    name = "Semester 6",
                    num = 6,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_601",
                            name = "Medicinal Chemistry III",
                            code = "BP601T",
                            description = "Antibiotics (Penicillins, Cephalosporins, Tetracyclines), Antimalarials, Sulfonamides, and Anti-tubercular agents.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_7",
                    name = "Semester 7",
                    num = 7,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_701",
                            name = "Instrumental Methods of Analysis",
                            code = "BP701T",
                            description = "UV-Visible Spectrophotometry, IR, Flame photometry, HPLC, GC, Column and Ion Exchange Chromatography.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "sem_8",
                    name = "Semester 8",
                    num = 8,
                    subjects = listOf(
                        PharmaSubject(
                            id = "bph_801",
                            name = "Biostatistics & Research Methodology",
                            code = "BP801T",
                            description = "Standard deviation, regression, Chi-square, Student t-test, ANOVA correlation and designing experiments.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                )
            )
        ),
        PharmaCourse(
            id = "dpharm",
            name = "Diploma in Pharmacy (D.Pharm)",
            type = CourseType.DPHARM,
            semesters = listOf(
                PharmaSemester(
                    id = "d_yr1",
                    name = "Year 1",
                    num = 1,
                    subjects = listOf(
                        PharmaSubject(
                            id = "dp_101",
                            name = "Pharmaceutics (D.Pharm)",
                            code = "ER20-11T",
                            description = "Introduction to history, metrology, packaging materials, pharmaceutical aid, size reduction, mixing, filtration, and drying.",
                            syllabus = listOf(
                                SyllabusUnit("Unit I", "Dosage Forms & History", listOf("Introduction to liquid dosage forms", "History of Pharmacopoeias in India", "Powdering pharmaceutical excipients"))
                            ),
                            notes = listOf(
                                PharmaNote(
                                    "dp_n1", "Excipients in Pharmaceutics", "Excipients",
                                    "**Excipients:** Pharmacologically inert substances formulated alongside the active pharmaceutical ingredient (API) of a medication.\n\n**Common types of excipients:**\n1. **Diluents / Fillers:** Add bulk to tablets (e.g., Lactose, Microcrystalline Cellulose - MCC).\n2. **Binders:** Hold the tablet ingredients together (e.g., Starch paste, Gelatin).\n3. **Disintegrants:** Help the tablet break apart in aqueous media (e.g., Sodium Starch Glycolate, Crospovidone).\n4. **Lubricants:** Prevent raw materials from sticking to machinery/die walls (e.g., Magnesium Stearate).\n5. **Glidants:** Improve powder flowability (e.g., Colloidal Silicon Dioxide, Talc).\n6. **Preservatives:** Prevent microbial growth (e.g., Methylparaben)." ,
                                    10
                                )
                            ),
                            videos = listOf(
                                PharmaVideo("dp_v1", "Excipients in Tablet Manufacturing", "Carewell Pharma Team", "20:10", "Excip10X", "Understand binders, disintegrants, lubricants and diluents.")
                            ),
                            mcqs = listOf(
                                PharmaMcq("dp_q1", "What is the function of Magnesium Stearate in tablet formulation?", listOf("Coloring Agent", "Binder", "Lubricant", "Disintegrant"), 2, "Magnesium Stearate is the most common hydrophobic lubricant, used to ease tablet ejection from dies."),
                                PharmaMcq("dp_q2", "Which excipient helps the tablet break apart rapidly when swallowed?", listOf("Glidant", "Disintegrant", "Diluent", "Plasticizer"), 1, "Disintegrants swell or attract water rapidly, causing the pill tablet to burst open in gastric juices.")
                            )
                        ),
                        PharmaSubject(
                            id = "dp_102",
                            name = "Pharmaceutical Chemistry",
                            code = "ER20-12T",
                            description = "Inorganic chemistry, volumetric analysis, limit tests, cardiovascular drugs, antihistamines, and vitamins.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "dp_103",
                            name = "Pharmacognosy (D.Pharm)",
                            code = "ER20-13T",
                            description = "Definition, history, classification, and study of alkaloids, glycosides, volatile oils, tannins, resin, and surgical dressings.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                ),
                PharmaSemester(
                    id = "d_yr2",
                    name = "Year 2",
                    num = 2,
                    subjects = listOf(
                        PharmaSubject(
                            id = "dp_201",
                            name = "Pharmacology (D.Pharm)",
                            code = "ER20-21T",
                            description = "Mechanism of actions, therapeutic applications, physiological roles, toxicity, and dosage definitions.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        ),
                        PharmaSubject(
                            id = "dp_202",
                            name = "Community Pharmacy & Management",
                            code = "ER20-22T",
                            description = "Community pharmacy setup, layout, customer relations, prescription handling, patient counseling, and inventory control.",
                            syllabus = emptyList(), notes = emptyList(), videos = emptyList(), mcqs = emptyList()
                        )
                    )
                )
            )
        )
    )

    fun getSubjectById(subjectId: String): PharmaSubject? {
        return courses.flatMap { it.semesters }.flatMap { it.subjects }.find { it.id == subjectId }
    }
}
