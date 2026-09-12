package com.sundys.zrmt

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ThemedActivity() {

    private var appliedTheme: String? = null

    private lateinit var store: LinkStore
    private lateinit var adapter: CardAdapter
    private lateinit var listView: ListView
    private lateinit var emptyView: View
    private lateinit var tvCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appliedTheme = AppPrefs.themeMode(this)
        store = LinkStore(this)

        tvCount = findViewById(R.id.tvCount)
        listView = findViewById(R.id.listView)
        emptyView = findViewById(R.id.emptyView)

        adapter = CardAdapter()
        listView.adapter = adapter
        adapter.onItemClick = { link ->
            store.markOpened(link.id)
            startActivity(RemoteActivity.intent(this, link))
        }
        adapter.onItemLongClick = { link, anchor -> showCardMenu(anchor, link) }

        findViewById<View>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, EditLinkActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 主题设置变化时重建以应用新配色
        if (appliedTheme != null && AppPrefs.themeMode(this) != appliedTheme) {
            recreate()
            return
        }
        refresh()
    }

    private fun refresh() {
        val list = store.load()
        adapter.submit(list)
        tvCount.text = getString(R.string.home_count, list.size)
        listView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCardMenu(anchor: View, link: Link) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "打开")
        popup.menu.add(0, 2, 0, "编辑")
        popup.menu.add(0, 3, 0, "复制链接")
        popup.menu.add(0, 4, 0, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    store.markOpened(link.id)
                    startActivity(RemoteActivity.intent(this, link))
                }
                2 -> EditLinkActivity.edit(this, link)
                3 -> {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("zcode", link.url))
                    Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
                }
                4 -> confirmDelete(link)
            }
            true
        }
        popup.show()
    }

    private fun confirmDelete(link: Link) {
        AlertDialog.Builder(this)
            .setTitle("删除卡片")
            .setMessage("确定删除「${link.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                store.delete(link.id)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private inner class CardAdapter : BaseAdapter() {

        private var items: List<Link> = emptyList()
        var onItemClick: ((Link) -> Unit)? = null
        var onItemLongClick: ((Link, View) -> Unit)? = null
        private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun submit(list: List<Link>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_card, parent, false)
            val link = items[position]
            val tvName = view.findViewById<TextView>(R.id.tvName)
            val tvVersion = view.findViewById<TextView>(R.id.tvVersion)
            val tvHost = view.findViewById<TextView>(R.id.tvHost)
            val tvTime = view.findViewById<TextView>(R.id.tvTime)

            tvName.text = link.name
            if (link.version.isEmpty()) {
                tvVersion.visibility = View.GONE
            } else {
                tvVersion.visibility = View.VISIBLE
                tvVersion.text = getString(R.string.version_badge, link.version)
            }
            tvHost.text = link.host
            tvTime.text = if (link.lastOpenedAt > 0)
                getString(R.string.last_opened, fmt.format(Date(link.lastOpenedAt)))
            else
                getString(R.string.added_at, fmt.format(Date(link.createdAt)))

            view.setOnClickListener { onItemClick?.invoke(link) }
            view.setOnLongClickListener { v ->
                onItemLongClick?.invoke(link, v)
                true
            }
            return view
        }
    }
}
