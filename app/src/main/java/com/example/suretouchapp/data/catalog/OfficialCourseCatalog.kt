package com.example.suretouchapp.data.catalog

data class OfficialCourseCatalogItem(
    val websiteId: String,
    val title: String,
    val eligibility: String,
    val skillModules: List<String>,
    val category: CourseCategory = CourseCategory.NON_MEDICAL
) {
    val websiteUrl: String
        get() = "https://www.suretrustforruralyouth.com/courses/$websiteId"
}

enum class CourseCategory(val label: String) {
    NON_MEDICAL("Non Medical"),
    MEDICAL("Medical")
}

/**
 * Current NON MEDICAL catalogue from the official SURE ProEd website.
 * Source: https://www.suretrustforruralyouth.com/courses/?category=NON%20MEDICAL
 * Last synchronized: 04 Aug 2026.
 */
val officialNonMedicalCourses = listOf(
    OfficialCourseCatalogItem(
        "23", "Robotics Applications",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("Robotics", "Sensors", "Automation")
    ),
    OfficialCourseCatalogItem(
        "32", "Cybersecurity & Ethical Hacking",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("Network Security", "Ethical Hacking", "Digital Forensics")
    ),
    OfficialCourseCatalogItem(
        "37", "Full Stack Web Development",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("Frontend", "Backend", "Databases")
    ),
    OfficialCourseCatalogItem(
        "40", "Java Applications",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("Core Java", "Spring", "REST APIs")
    ),
    OfficialCourseCatalogItem(
        "43", "Artificial Intelligence & Machine Learning",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("Python", "Machine Learning", "Deep Learning")
    ),
    OfficialCourseCatalogItem(
        "54", "Digital Marketing Applications",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("SEO", "Social Media", "Marketing Analytics")
    ),
    OfficialCourseCatalogItem(
        "59", "Embedded Systems & IOT",
        "B.Tech completed / B.Tech 3rd or 4th year",
        listOf("Embedded C", "Microcontrollers", "IoT")
    ),
    OfficialCourseCatalogItem(
        "62", "Integrated Internship in Financial Modelling & Valuation",
        "B.Com, BBA, MBA, CFA and CA aspirants",
        listOf("Financial Modelling", "Valuation", "Excel")
    ),
    OfficialCourseCatalogItem(
        "98", "PCB Designing",
        "B.Tech, ECE/EEE, 3rd year, 4th year pursuing and B.Tech just completed students can apply",
        listOf("Schematic Design", "PCB Layout", "Fabrication")
    ),
    OfficialCourseCatalogItem(
        "112", "Software Testing & Tools Applications",
        "B.Tech completed, B.Tech 3rd or 4th year",
        listOf("Manual Testing", "Automation", "QA Tools")
    ),
    OfficialCourseCatalogItem(
        "120", "UI / UX Designing",
        "Any Bachelor's degree",
        listOf("UX Research", "Wireframing", "Prototyping")
    ),
    OfficialCourseCatalogItem(
        "126", "Integrated VLSI designing - Concept to Silicon",
        "B.Tech (ECE) completed, or B.Tech (ECE) 3rd or 4th year / MBAs / any other PG",
        listOf("RTL Design", "Verification", "ASIC Flow")
    ),
    OfficialCourseCatalogItem(
        "129", "Industrial Automation",
        "B.Tech completed, B.Tech 3rd or 4th year - ECE, EEE and Instrumentation",
        listOf("PLC", "SCADA", "Instrumentation")
    ),
    OfficialCourseCatalogItem(
        "134", "Cloud Computing & DevOps Applications",
        "B.Tech completed, B.Tech 3rd or 4th year",
        listOf("Cloud", "CI/CD", "Containers")
    ),
    OfficialCourseCatalogItem(
        "136", "AutoCad, Solidworks & CREO",
        "B.Tech completed, B.Tech 3rd or 4th year / completed from Mechanical Stream",
        listOf("AutoCAD", "SolidWorks", "CREO")
    ),
    OfficialCourseCatalogItem(
        "145", "Salesforce Administration & Development",
        "Basic knowledge of computer programming",
        listOf("Administration", "Apex", "Lightning")
    ),
    OfficialCourseCatalogItem(
        "149", "SAP S/4 HANA or FICO",
        "Basic knowledge of computer programming",
        listOf("S/4 HANA", "FICO", "ERP")
    ),
    OfficialCourseCatalogItem(
        "150", "SAP ABAP Consultant",
        "Basic knowledge of computer programming",
        listOf("ABAP", "SAP Development", "ERP")
    ),
    OfficialCourseCatalogItem(
        "151", "Generative AI - Foundation to Applications",
        "Eligibility criteria include 3rd or 4th year B.Tech, MCA, MBA, or other postgraduate students",
        listOf("Prompt Engineering", "LLM Apps", "RAG")
    ),
    OfficialCourseCatalogItem(
        "154", "Data Structures & Algorithms in Java",
        "3rd or 4th year B.Tech, MCA, MBA, or other postgraduate students",
        listOf("Data Structures", "Algorithms", "Java")
    ),
    OfficialCourseCatalogItem(
        "162", "Data Analytics - Case-based, AI-Augmented, and Mentor-driver",
        "3rd or 4th year B.Tech, MCA, MBA, or other postgraduate students",
        listOf("SQL", "Power BI", "Analytics")
    ),
    OfficialCourseCatalogItem(
        "165", "Industry Focused, AI Augmented, Mentor Led Next Gen Civil Engineering Internship",
        "B.Tech completed / B.Tech 3rd or 4th year from Civil Engineering Stream",
        listOf("BIM", "Project Planning", "AI Tools")
    ),
    OfficialCourseCatalogItem(
        "166", "6-Month Project-based Actuarial Internship",
        "Pursuing/completed degree in Mathematics, Statistics, Actuarial Science, Economics, Engineering, or related fields; basic statistics, probability and Excel knowledge",
        listOf("Probability", "Statistics", "Excel")
    ),
    OfficialCourseCatalogItem(
        "167", "Six-Month Application-Based Full-Stack & QA Automation Internship",
        "3rd or 4th year B.Tech/BE, MCA, MBA, other postgraduate students, or B.Tech/BE and PG pass-outs after 2023",
        listOf("Full Stack", "QA Automation", "Projects")
    ),
    OfficialCourseCatalogItem(
        "168", "Six-Month AI-Integrated SAP MM Project Internship",
        "3rd or 4th year B.Tech/BE, MCA, MBA, other postgraduate students, or B.Tech/BE and PG pass-outs after 2023",
        listOf("SAP MM", "Materials Management", "Projects")
    ),
    OfficialCourseCatalogItem(
        "169", "Six-Month Online Project-Based and AI-Integrated Internship in Android Application Development",
        "3rd or 4th year B.Tech, MCA, MBA, or other postgraduate students",
        listOf("Kotlin", "Android", "AI Integration")
    )
)

/** Current MEDICAL catalogue from the same official source, synchronized 04 Aug 2026. */
val officialMedicalCourses = listOf(
    OfficialCourseCatalogItem(
        websiteId = "142",
        title = "Medical Coding",
        eligibility = "BSc, MSc, Pharmacy, MBBS and others with minimal knowledge in anatomy, physiology and medical terminology",
        skillModules = listOf("Medical Terminology", "ICD Coding", "Clinical Documentation"),
        category = CourseCategory.MEDICAL
    )
)

val officialCourses = officialNonMedicalCourses + officialMedicalCourses
