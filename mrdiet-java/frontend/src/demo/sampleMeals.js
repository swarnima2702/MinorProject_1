/**
 * DEMO ONLY — stands in for OpenAI so the demo works without an API key.
 * Builds a plan in exactly the JSON shape the real prompt asks GPT for, using
 * the same daily cuisine rotation and calorie formula as the Java backend.
 */

const dish = (name, ingredients, steps, benefits) => ({ name, ingredients, preparation_steps: steps, benefits });

const CUISINES = [
  {
    label: "North Indian",
    ritual: dish("Jeera-ajwain warm water", ["1 tsp jeera", "1/2 tsp ajwain", "250ml water"], ["Boil water with jeera and ajwain for 3 minutes on medium flame.", "Strain and sip warm on an empty stomach."], ["Kick-starts digestion for {goal}", "Reduces morning bloating"]),
    breakfast: [
      dish("Moong dal chilla with mint chutney", ["80g soaked moong dal", "1 small onion, chopped", "1 green chilli", "1 tsp oil", "2 tbsp mint chutney"], ["Grind soaked dal with chilli and a pinch of salt into a smooth batter.", "Heat a tawa on medium, spread a ladle of batter thin.", "Cook 2 minutes per side with a few drops of oil.", "Serve hot with mint chutney."], ["High plant protein keeps you full till lunch — supports {goal}", "Low glycaemic load for steady morning energy", "Fibre from whole dal aids gut health"]),
      dish("Paneer-stuffed multigrain paratha with dahi", ["2 small multigrain rotis (60g atta)", "60g paneer, grated", "1 tsp ghee", "100g curd"], ["Mix grated paneer with coriander, ajwain and salt.", "Stuff into rolled dough and seal the edges.", "Roast on a hot tawa, brushing lightly with ghee.", "Serve with a katori of fresh curd."], ["Casein from paneer gives slow-release protein for {goal}", "Probiotic curd supports digestion", "Whole grains give lasting energy"]),
    ],
    lunch: [
      dish("Rajma with jeera brown rice and kachumber", ["1 katori (150g) cooked rajma", "1 cup (120g) cooked brown rice", "1 cucumber + 1 tomato kachumber", "1 tsp ghee"], ["Pressure-cook soaked rajma for 5-6 whistles.", "Temper ghee with jeera, onion, tomato and spices; add rajma and simmer 10 minutes.", "Cook brown rice with a pinch of jeera.", "Serve with fresh kachumber."], ["Rajma + rice make a complete protein for {goal}", "Resistant starch keeps blood sugar stable", "High fibre supports satiety"]),
      dish("Palak paneer with 2 jowar rotis", ["150g palak", "70g paneer", "2 jowar rotis (60g flour)", "1 tsp oil"], ["Blanch palak for 2 minutes, then blend to a coarse puree.", "Saute garlic, onion and green chilli; add puree and simmer.", "Add paneer cubes and cook 3 minutes on low flame.", "Serve with fresh jowar rotis."], ["Iron from palak + vitamin C from tomato improves absorption", "Gluten-free jowar is gentle on digestion", "Protein-rich lunch powers your afternoon"]),
    ],
    dinner: [
      dish("Lauki chana dal with 1 phulka", ["150g lauki", "40g chana dal", "1 phulka", "1/2 tsp ghee"], ["Pressure-cook chana dal with chopped lauki and turmeric.", "Temper with ghee, jeera and hing.", "Serve warm with one phulka."], ["Light, water-rich dinner for overnight recovery", "Gentle on digestion for better sleep", "Supports {goal} without heaviness"]),
      dish("Mixed veg dalia khichdi", ["50g broken wheat (dalia)", "30g moong dal", "100g mixed vegetables", "1 tsp ghee"], ["Roast dalia lightly in ghee.", "Add dal, vegetables, turmeric and 3 cups water.", "Pressure-cook for 3 whistles and serve with curd."], ["Comforting one-pot meal with balanced macros", "Fibre from dalia supports gut health", "Easy to digest late in the day"]),
    ],
    snacks: [
      dish("Roasted makhana", ["30g makhana", "1/2 tsp ghee", "pinch of chaat masala"], ["Dry roast makhana on low flame for 6 minutes.", "Toss with ghee and chaat masala."], ["Low-calorie crunchy snack for {goal}", "Magnesium supports calm energy"]),
      dish("Masala buttermilk with roasted chana", ["200ml chaas", "25g roasted chana"], ["Whisk curd with water, jeera powder and mint.", "Serve chilled with roasted chana."], ["Probiotics plus protein", "Keeps you hydrated through the afternoon"]),
    ],
  },
  {
    label: "South Indian",
    ritual: dish("Warm water with lemon and a pinch of turmeric", ["250ml warm water", "1/2 lemon", "pinch of haldi"], ["Squeeze lemon into warm (not boiling) water.", "Stir in haldi and sip slowly."], ["Vitamin C and curcumin to start the day", "Gentle hydration for {goal}"]),
    breakfast: [
      dish("3 ragi idlis with sambar", ["3 ragi idlis (120g batter)", "1 katori sambar", "1 tbsp coconut chutney"], ["Steam ragi-urad batter in idli moulds for 12 minutes.", "Prepare sambar with toor dal, drumstick and tamarind.", "Serve hot with a little coconut chutney."], ["Fermented batter supports gut bacteria", "Ragi is rich in calcium and fibre — great for {goal}", "Sambar adds plant protein"]),
      dish("Vegetable rava upma with peanuts", ["50g rava", "80g mixed vegetables", "10g peanuts", "1 tsp oil"], ["Dry roast rava until fragrant.", "Temper mustard, curry leaves, urad dal and peanuts.", "Add vegetables and water, then stir in rava until fluffy."], ["Quick complex carbs for morning energy", "Peanuts add healthy fats and protein", "Vegetables boost micronutrients"]),
    ],
    lunch: [
      dish("Curd rice with beans poriyal and rasam", ["1 cup (120g) cooked rice", "100g curd", "100g beans poriyal", "1 katori rasam"], ["Mash rice with curd and temper with mustard and curry leaves.", "Stir-fry beans with grated coconut.", "Serve with hot pepper rasam."], ["Cooling, probiotic-rich lunch", "Rasam's pepper and tamarind aid digestion", "Balanced plate for {goal}"]),
      dish("Brown rice sambar sadam with keerai", ["1 cup cooked brown rice", "1 katori sambar", "100g keerai (spinach) poriyal"], ["Mix brown rice with thick vegetable sambar.", "Saute keerai with garlic and coconut.", "Serve warm."], ["Plant protein and fibre in one bowl", "Iron-rich greens for energy", "Slow carbs avoid the afternoon crash"]),
    ],
    dinner: [
      dish("Pesarattu with ginger chutney", ["2 pesarattu (80g green moong)", "1 tbsp ginger chutney"], ["Grind soaked green moong with ginger and chilli.", "Spread thin on a hot tawa and cook until crisp.", "Serve with ginger chutney."], ["Protein-rich, light dinner for {goal}", "Ginger aids night-time digestion"]),
      dish("Vegetable appam with stew", ["2 appams", "1 katori vegetable stew in light coconut milk"], ["Cook appams in an appachatti until edges crisp.", "Simmer vegetables in thin coconut milk with whole spices.", "Serve together."], ["Fermented appam is easy to digest", "Coconut fats keep you satisfied"]),
    ],
    snacks: [
      dish("Sundal (chickpea salad)", ["60g boiled chickpeas", "1 tbsp grated coconut", "curry leaves"], ["Temper chickpeas with mustard and curry leaves.", "Finish with coconut and lemon."], ["Protein + fibre snack for {goal}", "Keeps cravings away"]),
      dish("Neer mor (spiced buttermilk)", ["200ml buttermilk", "ginger, curry leaves"], ["Blend buttermilk with ginger and curry leaves.", "Serve chilled."], ["Hydrating and probiotic", "Cools the body"]),
    ],
  },
  {
    label: "Bengali",
    ritual: dish("Soaked methi seed water", ["1 tsp methi seeds", "250ml water"], ["Soak methi overnight.", "Warm slightly and drink with the seeds."], ["Helps regulate blood sugar for {goal}", "Supports digestion"]),
    breakfast: [
      dish("Chirer pulao (vegetable poha)", ["50g poha", "80g peas and carrots", "10g peanuts", "1 tsp mustard oil"], ["Rinse poha and drain.", "Temper mustard oil with whole spices, add vegetables.", "Fold in poha and cook 3 minutes."], ["Light carbs with iron from poha", "Peas add plant protein"]),
      dish("Moong dal cheela with tomato chutney", ["70g moong dal batter", "1 tbsp tomato chutney"], ["Spread batter thin and cook both sides.", "Serve with fresh tomato chutney."], ["High protein start for {goal}", "Low in fat"]),
    ],
    lunch: [
      dish("Cholar dal with rice and shukto", ["1 katori cholar dal", "1 cup cooked rice", "100g shukto (mixed vegetables)"], ["Cook chana dal with coconut bits and spices.", "Prepare shukto with bitter gourd, raw banana and drumstick.", "Serve with steamed rice."], ["Bitter gourd supports healthy blood sugar", "Dal + rice give complete protein for {goal}"]),
      dish("Paneer jhol with rice", ["80g paneer", "1 cup cooked rice", "1 small potato, 1 tomato"], ["Lightly sear paneer cubes with turmeric.", "Simmer in a thin tomato-cumin Bengali gravy.", "Serve with rice."], ["Protein for muscle repair and satiety", "Light gravy keeps the meal low in fat"]),
    ],
    dinner: [
      dish("Lau ghonto with 1 roti", ["150g lauki", "20g moong dal", "1 roti"], ["Cook grated lauki with roasted moong dal and ginger.", "Finish with a little ghee.", "Serve with one roti."], ["Light, hydrating dinner", "Gentle on digestion for {goal}"]),
      dish("Posto vegetables with dal", ["100g potato and beans", "1 tbsp posto paste", "1 katori masoor dal"], ["Cook vegetables in a light posto paste.", "Serve with thin masoor dal."], ["Comforting, balanced dinner", "Plant protein from dal"]),
    ],
    snacks: [
      dish("Jhal muri (light)", ["25g puffed rice", "cucumber, onion, tomato", "1/2 tsp mustard oil"], ["Toss puffed rice with chopped vegetables and spices."], ["Low-calorie crunchy snack", "Fibre from vegetables"]),
      dish("Mishti doi (small) with fruit", ["80g mishti doi", "1 small guava"], ["Serve chilled with sliced guava."], ["Probiotic treat", "Vitamin C from guava"]),
    ],
  },
  {
    label: "Gujarati",
    ritual: dish("Saunf-elaichi water", ["1 tsp saunf", "1 elaichi", "250ml water"], ["Simmer saunf and elaichi for 3 minutes.", "Sip warm."], ["Soothes digestion", "Freshens the gut for {goal}"]),
    breakfast: [
      dish("Methi thepla with curd", ["2 methi theplas (60g atta)", "100g curd"], ["Knead atta with methi, curd and spices.", "Roll thin and roast with minimal oil.", "Serve with curd."], ["Methi supports blood sugar control", "Whole wheat for sustained energy"]),
      dish("Besan dhokla with green chutney", ["4 pieces khaman dhokla (60g besan)", "1 tbsp green chutney"], ["Steam fermented besan batter for 15 minutes.", "Temper with mustard and curry leaves.", "Serve with chutney."], ["Steamed, low-fat protein snack-meal", "Fermentation aids digestion"]),
    ],
    lunch: [
      dish("Gujarati kadhi with khichdi", ["1 katori kadhi", "1 cup moong dal khichdi", "1 tsp ghee"], ["Cook khichdi with moong dal and rice.", "Make kadhi with curd, besan and ginger.", "Serve with ghee."], ["Easy-to-digest complete protein", "Probiotic curd in kadhi"]),
      dish("Undhiyu with bajra rotla", ["150g undhiyu (mixed winter vegetables)", "1 bajra rotla"], ["Slow-cook vegetables with methi muthia and green masala.", "Serve with a fresh bajra rotla."], ["Bajra is rich in iron and fibre", "Seasonal vegetables add antioxidants"]),
    ],
    dinner: [
      dish("Handvo slice with salad", ["1 large slice handvo (70g mixed dal-rice batter)", "1 bowl salad"], ["Bake or pan-cook handvo batter with lauki.", "Serve with a fresh salad."], ["Balanced carbs and protein", "Light enough for dinner"]),
      dish("Moong dal soup with 1 bhakri", ["1 bowl moong dal soup", "1 small bhakri"], ["Simmer moong dal with ginger and turmeric.", "Serve with a bhakri."], ["Warm, protein-rich and light", "Supports overnight recovery"]),
    ],
    snacks: [
      dish("Khakhra with peanuts", ["2 methi khakhras", "10g roasted peanuts"], ["Serve khakhras with a few peanuts."], ["Crunchy, portion-controlled snack", "Healthy fats from peanuts"]),
      dish("Fruit chaat", ["1 apple", "1 orange", "chaat masala"], ["Chop fruit and toss with chaat masala."], ["Vitamins and fibre", "Natural sweetness without sugar"]),
    ],
  },
  {
    label: "Maharashtrian",
    ritual: dish("Amla water", ["1 tbsp amla juice", "200ml warm water"], ["Mix amla juice into warm water.", "Drink before breakfast."], ["Vitamin C for immunity", "Supports metabolism for {goal}"]),
    breakfast: [
      dish("Kanda poha with peanuts", ["50g poha", "1 onion", "10g peanuts", "1 tsp oil"], ["Rinse poha and set aside.", "Temper mustard, curry leaves and peanuts; add onion.", "Add poha, turmeric and lemon; steam 2 minutes."], ["Light, iron-rich breakfast", "Quick energy for the morning"]),
      dish("Thalipeeth with curd", ["2 small thalipeeth (60g bhajani flour)", "100g curd"], ["Knead multigrain bhajani flour with onion and spices.", "Pat thin on a tawa and cook with a little oil.", "Serve with curd."], ["Multigrain fibre and protein for {goal}", "Keeps you full for hours"]),
    ],
    lunch: [
      dish("Varan bhaat with bhaji", ["1 katori varan (toor dal)", "1 cup rice", "100g seasonal bhaji"], ["Cook toor dal with turmeric and hing.", "Serve over steamed rice with a vegetable bhaji.", "Add a squeeze of lemon."], ["Classic balanced plate", "Easy to digest"]),
      dish("Misal (sprouts) with 1 pav", ["1 bowl sprouted matki misal", "1 whole-wheat pav", "onion, lemon"], ["Cook sprouts in a light kat (gravy).", "Top with onion and coriander.", "Serve with one pav."], ["Sprouts are packed with protein and enzymes", "High fibre for {goal}"]),
    ],
    dinner: [
      dish("Zunka with bhakri", ["1 katori zunka (besan)", "1 jowar bhakri"], ["Cook besan with onion, garlic and chilli until crumbly.", "Serve with jowar bhakri."], ["Protein from besan", "Gluten-free jowar"]),
      dish("Pithla with brown rice", ["1 katori pithla", "1/2 cup brown rice"], ["Simmer besan in tempered water until thick.", "Serve with brown rice."], ["Warm, comforting and light", "Plant protein for recovery"]),
    ],
    snacks: [
      dish("Sabudana-free roasted chivda", ["30g roasted poha chivda"], ["Serve a small bowl of roasted chivda."], ["Crunchy, light snack", "Portion-controlled carbs"]),
      dish("Kokum sharbat (no sugar) with nuts", ["200ml kokum water", "5 almonds"], ["Mix kokum concentrate with water and jeera.", "Serve with almonds."], ["Cooling and hydrating", "Healthy fats from almonds"]),
    ],
  },
  {
    label: "Rajasthani",
    ritual: dish("Ajwain-saunf water", ["1/2 tsp ajwain", "1/2 tsp saunf", "250ml water"], ["Boil and strain.", "Sip warm."], ["Supports digestion", "Reduces gas and bloating"]),
    breakfast: [
      dish("Bajra khichdi with curd", ["40g bajra", "20g moong dal", "100g curd"], ["Pressure-cook soaked bajra and dal.", "Temper with ghee and jeera.", "Serve with curd."], ["Bajra is rich in magnesium and fibre", "Warming winter breakfast"]),
      dish("Besan chilla with paneer", ["60g besan", "40g paneer", "onion, tomato"], ["Make a thin besan batter with vegetables.", "Cook chilla and fill with grated paneer."], ["High-protein start for {goal}", "Low glycaemic"]),
    ],
    lunch: [
      dish("Gatte ki sabzi with 2 rotis", ["1 katori gatte sabzi (besan dumplings)", "2 whole-wheat rotis"], ["Boil besan gatte and slice.", "Simmer in a light curd gravy.", "Serve with rotis."], ["Protein from besan", "Traditional, satisfying lunch"]),
      dish("Ker sangri with bajra roti", ["100g ker sangri", "1 bajra roti", "1 katori dal"], ["Cook soaked ker sangri with spices.", "Serve with bajra roti and dal."], ["Desert beans rich in fibre", "Iron from bajra"]),
    ],
    dinner: [
      dish("Panchmel dal with 1 roti", ["1 katori panchmel dal", "1 roti"], ["Cook five dals together with ginger.", "Temper with ghee and hing."], ["Five dals = varied amino acids", "Light and warming"]),
      dish("Lauki-moong sabzi with roti", ["150g lauki", "30g moong dal", "1 roti"], ["Cook lauki with moong dal and mild spices."], ["Light and hydrating dinner", "Good for sleep"]),
    ],
    snacks: [
      dish("Roasted chana with jaggery bit", ["25g roasted chana", "5g gur"], ["Serve chana with a small piece of gur."], ["Protein and iron", "Satisfies sweet cravings"]),
      dish("Chaas with roasted jeera", ["200ml chaas"], ["Whisk curd with water and roasted jeera."], ["Probiotic and cooling", "Aids digestion"]),
    ],
  },
  {
    label: "Pan-Indian Fusion",
    ritual: dish("Cinnamon-ginger water", ["1 small cinnamon stick", "1 inch ginger", "250ml water"], ["Simmer for 5 minutes and strain."], ["Supports insulin sensitivity for {goal}", "Anti-inflammatory start"]),
    breakfast: [
      dish("Oats vegetable cheela", ["40g oats", "20g besan", "60g vegetables"], ["Blend oats and besan into a batter with vegetables.", "Cook thin cheelas on a non-stick tawa."], ["Beta-glucan from oats supports heart health", "Protein + fibre combo"]),
      dish("Ragi porridge with nuts", ["30g ragi flour", "200ml milk", "5 almonds"], ["Cook ragi in milk, stirring constantly.", "Top with chopped almonds."], ["Calcium-rich ragi", "Slow-release energy"]),
    ],
    lunch: [
      dish("Quinoa vegetable pulao with raita", ["50g quinoa", "100g vegetables", "100g cucumber raita"], ["Rinse quinoa and cook with whole spices and vegetables.", "Serve with raita."], ["Complete plant protein", "High fibre for {goal}"]),
      dish("Paneer tikka wrap", ["1 whole-wheat roti", "70g paneer tikka", "salad, mint chutney"], ["Grill marinated paneer.", "Wrap in roti with salad and chutney."], ["Protein-packed lunch", "Easy to carry"]),
    ],
    dinner: [
      dish("Oats moong khichdi", ["30g oats", "30g moong dal", "vegetables"], ["Pressure-cook oats, dal and vegetables with turmeric."], ["Light and fibre-rich", "Gentle on digestion"]),
      dish("Grilled tofu/paneer with sauteed vegetables", ["80g paneer or tofu", "150g vegetables"], ["Grill paneer/tofu with spices.", "Saute vegetables with garlic."], ["Low-carb protein dinner", "Supports muscle recovery"]),
    ],
    snacks: [
      dish("Greek-style dahi with seeds", ["100g hung curd", "1 tsp flax seeds"], ["Top hung curd with seeds."], ["Protein and omega-3", "Keeps you full"]),
      dish("Sprouts chaat", ["60g sprouts", "onion, tomato, lemon"], ["Toss sprouts with vegetables and lemon."], ["Enzyme-rich protein snack", "High fibre"]),
    ],
  },
];

export function targetCalories(profile) {
  const w = Number(profile.weight) || 70;
  let h = Number(profile.height) || 170;
  if (h < 3) h *= 100;
  else if (h < 10) h *= 30.48;
  const age = Number(profile.age) || 30;
  const g = String(profile.gender || "male").toLowerCase();
  const female = g === "female" || g === "women";
  const bmr = 10 * w + 6.25 * h - 5 * age + (female ? -161 : 5);
  const mult = { sedentary: 1.2, light: 1.375, moderate: 1.55, active: 1.725 }[profile.activity_level] || 1.55;
  const tdee = bmr * mult;
  const goal = String(profile.goal || "weight-loss");
  return Math.round(goal.includes("weight-loss") ? tdee - 500 : goal.includes("muscle-gain") ? tdee + 300 : tdee);
}

function split(goal) {
  if (goal.includes("loss")) return [0.28, 0.42, 0.3];
  if (goal.includes("gain")) return [0.32, 0.48, 0.2];
  return [0.22, 0.52, 0.26];
}

/** Allergens from the profile ("Peanuts, milk") → word stems to avoid ("peanut", "milk"). */
const allergyStems = (profile) =>
  String(profile.allergies || "").split(/[,;/]/).map((a) => a.trim().toLowerCase().replace(/(es|s)$/, "")).filter((a) => a.length > 2);

/** Same rule the real prompt gives GPT: allergens are never served. */
function withoutAllergens(d, stems) {
  if (!stems.length) return d;
  const hit = (text) => stems.some((a) => text.toLowerCase().includes(a));
  return {
    ...d,
    name: d.name.replace(/ (with|and) [^,()]+/gi, (m) => (hit(m) ? "" : m)),
    ingredients: d.ingredients.filter((i) => !hit(i)),
    preparation_steps: d.preparation_steps.map((st) => (hit(st) ? st.replace(/,?\s*(and )?\w*\s*(peanuts?|cashews?|almonds?|walnuts?|nuts?)\b/gi, "") : st)),
    benefits: d.benefits.filter((b) => !hit(b)),
  };
}

function withMacros(d, kcal, goal) {
  const [p, c, f] = split(goal);
  const fill = (s) => s.replaceAll("{goal}", goal);
  return {
    ...d,
    benefits: d.benefits.map(fill),
    calories: Math.round(kcal),
    protein: Math.round((kcal * p) / 4),
    carbs: Math.round((kcal * c) / 4),
    fat: Math.round((kcal * f) / 9),
    fiber: Math.max(2, Math.round(kcal / 80)),
  };
}

/** A full day plan, same structure as the GPT output. */
export function samplePlan(dayNumber, profile, notes, phase) {
  const goal = String(profile.goal || "weight-loss").split(",")[0].trim() || "weight-loss";
  const kcal = targetCalories(profile);
  const c = CUISINES[(((dayNumber - 1) % CUISINES.length) + CUISINES.length) % CUISINES.length];
  const [p, cb, f] = split(goal);
  const fill = (s) => s.replaceAll("{goal}", goal);
  const stems = allergyStems(profile);
  const safe = (d) => withoutAllergens(d, stems);

  const mealSlot = (time, title, pair, share, tip) => ({
    time,
    title,
    type: "meal",
    name: `${c.label} ${title.toLowerCase()}`,
    option_1: withMacros(safe(pair[0]), kcal * share, goal),
    option_2: withMacros(safe(pair[1]), kcal * share - 10, goal),
    suggestions: tip,
  });
  const snackSlot = (time, title, d, share) => ({ time, title, type: "meal", ...withMacros(safe(d), kcal * share, goal) });

  const summaryBits = [
    `Day ${dayNumber} leans on ${c.label} home cooking to hit ${kcal} kcal with a protein-forward split for ${goal}.`,
    "Breakfast and lunch carry most of the energy so your evening meal stays light for better sleep and digestion.",
  ];
  if (notes) summaryBits.push(`Because you mentioned "${notes}", today's choices favour gentle, easy-to-digest foods.`);
  if (phase) summaryBits.push(`You're in your ${phase.phase} phase (cycle day ${phase.cycleDay}), so iron and magnesium-rich foods are emphasised.`);

  return {
    day_title: `Day ${dayNumber}: ${c.label} Balance`,
    daily_objectives: [
      `Target Focus: stay within ${kcal} kcal while feeling full`,
      `Health Metric: steady energy and digestion for ${goal}`,
      `Macro Focus: ~${Math.round((kcal * p) / 4)}g protein spread across 5 meals`,
      "Mindset/Consistency: eat at regular times today",
      "Metabolic trigger: 10-minute walk after lunch and dinner",
    ],
    day_summary: summaryBits.join(" "),
    target_stats: {
      calories: kcal,
      protein: Math.round((kcal * p) / 4),
      carbs: Math.round((kcal * cb) / 4),
      fat: Math.round((kcal * f) / 9),
      fiber: Math.round(kcal / 60),
    },
    water_intake: {
      total_liters: 2.5,
      schedule: [
        "6:30 AM - 2 glasses (500ml) warm water on empty stomach",
        "8:00 AM - 1 glass (250ml) with breakfast",
        "11:00 AM - 1 glass (250ml) mid-morning",
        "1:00 PM - 1 glass (250ml) before lunch",
        "3:00 PM - 1 glass (250ml) afternoon",
        "5:00 PM - 1 glass (250ml) with evening snack",
        "8:00 PM - 1 glass (250ml) with/after dinner",
      ],
      tips: [
        `Sip water steadily — thirst is often mistaken for hunger when working on ${goal}.`,
        "Add a few slices of kheera or a squeeze of nimbu for flavour without calories.",
        "Keep tea/coffee to 2 cups and avoid them within an hour of meals.",
      ],
    },
    timeline: [
      {
        time: "6:30 AM",
        title: "Wake-Up Ritual",
        type: "ritual",
        items: [{ name: c.ritual.name, quantity: "200-250ml", instruction: c.ritual.preparation_steps.join(" "), benefit: c.ritual.benefits.map(fill) }],
      },
      mealSlot("8:00 AM", "Breakfast", c.breakfast, 0.25, "Eat within an hour of waking to keep energy steady."),
      snackSlot("11:00 AM", "Mid-Morning Snack", c.snacks[0], 0.1),
      mealSlot("1:30 PM", "Lunch", c.lunch, 0.3, "Start with the salad or sabzi, then the grains."),
      snackSlot("4:30 PM", "Evening Snack", c.snacks[1], 0.1),
      mealSlot("7:30 PM", "Dinner", c.dinner, 0.25, "Finish dinner 2-3 hours before bed."),
    ],
    daily_dos: [
      `Fill half your plate with vegetables to support ${goal}`,
      "Eat breakfast within 60 minutes of waking",
      `Take a 20-30 minute walk aligned with your ${profile.activity_level || "moderate"} activity level`,
      phase ? `Add a handful of iron-rich foods for your ${phase.phase} phase` : "Choose seasonal, locally available produce",
    ],
    daily_donts: [
      "Avoid fried namkeen and sugary chai between meals",
      "Don't skip meals — it leads to overeating at night",
      "Avoid cold drinks with meals; prefer room-temperature water",
      "Don't eat directly in front of a screen — portion control slips",
    ],
    summary_table: {
      total_calories: kcal,
      focus: goal,
      expected_result: "Stable energy, fewer cravings and steady progress towards your goal.",
      key_nutrients: "Protein (satiety), fibre (gut health), iron (energy)",
    },
  };
}

/** Meal swap: what the user has → a dish matching the target macros. */
export function sampleSubstitution(userHas, t = {}) {
  const items = String(userHas).split(/,| and /).map((s) => s.trim()).filter(Boolean).slice(0, 5);
  const kcal = Number(t.calories) || 500;
  return {
    name: `${items[0] ? items[0][0].toUpperCase() + items[0].slice(1) : "Home-style"} bowl`,
    calories: Math.round(kcal),
    protein: Math.round(Number(t.protein) || kcal * 0.07),
    carbs: Math.round(Number(t.carbs) || kcal * 0.12),
    fat: Math.round(Number(t.fat) || kcal * 0.03),
    fiber: Math.max(3, Math.round(kcal / 90)),
    ingredients: items.map((it, i) => `${[150, 100, 80, 50, 30][i]}g ${it}`),
    preparation_steps: [
      "Wash and chop everything into bite-size pieces.",
      "Saute aromatics (jeera, garlic, ginger) in 1 tsp oil on medium flame.",
      "Add the main ingredients, season with haldi and salt, cook 8-10 minutes.",
      "Finish with lemon and fresh coriander.",
    ],
    quantity_tips: "Portions are sized to match the meal you're replacing.",
    benefits: ["Matches the calories and protein of your original meal", "Uses what you already have at home"],
  };
}
