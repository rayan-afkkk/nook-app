package com.nook.app.feature.chat

/** A compact, curated emoji set (no extra dependency). */
object EmojiCatalog {
    val categories: List<Pair<String, List<String>>> = listOf(
        "Smileys" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖".split(" "),
        "Gestures" to "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 💪 🫶 👀 🧠 🫡 🤷 🤦 🙋 💁 🙆 🙅".split(" "),
        "Hearts" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ✨ ⭐ 🌟 💫 🔥 💯 💥 💢 💤 🎉 🎊 🎈 🎁 🏆 🥇".split(" "),
        "Food" to "🍕 🍔 🍟 🌭 🌮 🌯 🥙 🍣 🍜 🍝 🍛 🍗 🥩 🥓 🍳 🥞 🧇 🥐 🍩 🍪 🎂 🍰 🧁 🍫 🍿 🍦 🍉 🍓 🍒 🍑 🥭 🍍 🥑 🌶️ ☕ 🧋 🍵 🥤 🍺 🍻 🥂 🍷".split(" "),
        "Things" to "⚽ 🏀 🏈 🎾 🎮 🕹️ 🎧 🎤 🎸 🎬 📸 📱 💻 ⌚ 💡 📚 ✏️ 📌 🔒 🔑 🚗 ✈️ 🚀 🏠 🌍 🌙 ☀️ 🌈 ☔ ❄️ 🌊 🌸 🌻 🍀 🐶 🐱 🐻 🐼 🦊 🐸 🦄 🐝".split(" "),
    )
}
