package com.philkes.notallyx.presentation.activity.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PostPDFGenerator
import android.transition.TransitionManager
import android.view.Menu
import android.view.Menu.CATEGORY_CONTAINER
import android.view.Menu.CATEGORY_SYSTEM
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialFade
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.ActivityMainBinding
import com.philkes.notallyx.databinding.DialogColorBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.movedToResId
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.view.main.ColorAdapter
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.view.misc.tristatecheckbox.TriStateCheckBox
import com.philkes.notallyx.presentation.view.misc.tristatecheckbox.setMultiChoiceTriStateItems
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.utils.Operations
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : LockedActivity<ActivityMainBinding>() {

    private lateinit var navController: NavController
    private lateinit var configuration: AppBarConfiguration
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>

    private val model: BaseNoteModel by viewModels()
    private val actionModeCancelCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                model.actionMode.close(true)
            }
        }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(configuration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.Toolbar)
        setupFAB()
        setupMenu()
        setupActionMode()
        setupNavigation()
        setupSearch()

        setupActivityResultLaunchers()
        onBackPressedDispatcher.addCallback(this, actionModeCancelCallback)
    }

    private fun setupFAB() {
        binding.TakeNote.setOnClickListener {
            val intent = Intent(this, EditNoteActivity::class.java)
            startActivity(intent)
        }
        binding.MakeList.setOnClickListener {
            val intent = Intent(this, EditListActivity::class.java)
            startActivity(intent)
        }
    }

    private var labelsMenuItems: List<MenuItem> = listOf()
    private var labelsMoreMenuItem: MenuItem? = null
    private var labels: List<String> = listOf()

    private fun setupMenu() {
        binding.NavigationView.menu.apply {
            add(0, R.id.Notes, 0, R.string.notes).setCheckable(true).setIcon(R.drawable.home)
            NotallyDatabase.getDatabase(application).value.getLabelDao().getAll().observe(
                this@MainActivity
            ) { labels ->
                this@MainActivity.labels = labels
                setupLabelsMenuItems(labels, preferences.maxLabels.value)
            }
            add(2, R.id.Deleted, CATEGORY_SYSTEM + 1, R.string.deleted)
                .setCheckable(true)
                .setIcon(R.drawable.delete)
            add(2, R.id.Archived, CATEGORY_SYSTEM + 2, R.string.archived)
                .setCheckable(true)
                .setIcon(R.drawable.archive)
            add(3, R.id.Settings, CATEGORY_SYSTEM + 3, R.string.settings)
                .setCheckable(true)
                .setIcon(R.drawable.settings)
        }
        model.preferences.labelsHiddenInNavigation.observe(this) { hiddenLabels ->
            hideLabelsInNavigation(hiddenLabels, model.preferences.maxLabels.value)
        }
        model.preferences.maxLabels.observe(this) { maxLabels ->
            binding.NavigationView.menu.setupLabelsMenuItems(labels, maxLabels)
        }
    }

    private fun Menu.setupLabelsMenuItems(labels: List<String>, maxLabelsToDisplay: Int) {
        removeGroup(1)
        add(1, R.id.Labels, CATEGORY_CONTAINER + 1, R.string.labels)
            .setCheckable(true)
            .setIcon(R.drawable.label_more)
        labelsMenuItems =
            labels
                .mapIndexed { index, label ->
                    add(1, R.id.DisplayLabel, CATEGORY_CONTAINER + index + 2, label)
                        .setCheckable(true)
                        .setVisible(index < maxLabelsToDisplay)
                        .setIcon(R.drawable.label)
                        .setOnMenuItemClickListener {
                            val bundle =
                                Bundle().apply { putString(Constants.SelectedLabel, label) }
                            navController.navigate(R.id.DisplayLabel, bundle)
                            false
                        }
                }
                .toList()

        labelsMoreMenuItem =
            if (labelsMenuItems.size > maxLabelsToDisplay) {
                add(
                        1,
                        R.id.Labels,
                        CATEGORY_CONTAINER + labelsMenuItems.size + 2,
                        getString(R.string.more, labelsMenuItems.size - maxLabelsToDisplay),
                    )
                    .setCheckable(true)
                    .setIcon(R.drawable.label)
            } else null
        configuration = AppBarConfiguration(binding.NavigationView.menu, binding.DrawerLayout)
        setupActionBarWithNavController(navController, configuration)
        hideLabelsInNavigation(model.preferences.labelsHiddenInNavigation.value, maxLabelsToDisplay)
    }

    private fun hideLabelsInNavigation(hiddenLabels: Set<String>, maxLabelsToDisplay: Int) {
        var visibleLabels = 0
        labelsMenuItems.forEachIndexed { idx, menuItem ->
            val visible =
                !hiddenLabels.contains(menuItem.title) && visibleLabels < maxLabelsToDisplay
            menuItem.setVisible(visible)
            if (visible) {
                visibleLabels++
            }
        }
        labelsMoreMenuItem?.setTitle(getString(R.string.more, labels.size - visibleLabels))
    }

    private fun setupActionMode() {
        binding.ActionMode.setNavigationOnClickListener { model.actionMode.close(true) }

        val transition =
            MaterialFade().apply {
                secondaryAnimatorProvider = null
                excludeTarget(binding.NavHostFragment, true)
                excludeChildren(binding.NavHostFragment, true)
                excludeTarget(binding.TakeNote, true)
                excludeTarget(binding.MakeList, true)
                excludeTarget(binding.NavigationView, true)
            }

        model.actionMode.enabled.observe(this) { enabled ->
            TransitionManager.beginDelayedTransition(binding.RelativeLayout, transition)
            if (enabled) {
                binding.Toolbar.visibility = View.GONE
                binding.ActionMode.visibility = View.VISIBLE
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.Toolbar.visibility = View.VISIBLE
                binding.ActionMode.visibility = View.GONE
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
            }
            actionModeCancelCallback.isEnabled = enabled
        }

        val menu = binding.ActionMode.menu
        val pinned = menu.add(R.string.pin, R.drawable.pin, MenuItem.SHOW_AS_ACTION_ALWAYS) {}
        val share = menu.add(R.string.share, R.drawable.share) { share() }
        val labels =
            menu.add(R.string.labels, R.drawable.label, MenuItem.SHOW_AS_ACTION_ALWAYS) { label() }

        val export = createExportMenu(menu)

        val changeColor = menu.add(R.string.change_color, R.drawable.change_color) { changeColor() }
        val delete =
            menu.add(R.string.delete, R.drawable.delete, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                moveNotes(Folder.DELETED)
            }
        val archive = menu.add(R.string.archive, R.drawable.archive) { moveNotes(Folder.ARCHIVED) }
        val restore = menu.add(R.string.restore, R.drawable.restore) { moveNotes(Folder.NOTES) }
        val unarchive =
            menu.add(R.string.unarchive, R.drawable.unarchive) { moveNotes(Folder.NOTES) }
        val deleteForever = menu.add(R.string.delete_forever, R.drawable.delete) { deleteForever() }

        model.actionMode.count.observe(this) { count ->
            if (count == 0) {
                menu.forEach { item -> item.setVisible(false) }
            } else {
                binding.ActionMode.title = count.toString()

                val baseNotes = model.actionMode.selectedNotes.values
                val showPin = baseNotes.any { !it.pinned }
                if (showPin) {
                    pinned.setTitle(R.string.pin).setIcon(R.drawable.pin).onClick {
                        model.pinBaseNotes(true)
                    }
                } else {
                    pinned.setTitle(R.string.unpin).setIcon(R.drawable.unpin).onClick {
                        model.pinBaseNotes(false)
                    }
                }

                pinned.setVisible(true)
                share.setVisible(count == 1)
                labels.setVisible(true)
                export.setVisible(count == 1)
                changeColor.setVisible(true)

                val folder = baseNotes.first().folder
                delete.setVisible(folder == Folder.NOTES || folder == Folder.ARCHIVED)
                archive.setVisible(folder == Folder.NOTES)
                restore.setVisible(folder == Folder.DELETED)
                unarchive.setVisible(folder == Folder.ARCHIVED)
                deleteForever.setVisible(folder == Folder.DELETED)
            }
        }
    }

    private fun createExportMenu(menu: Menu): MenuItem {
        return menu
            .addSubMenu(R.string.export)
            .apply {
                setIcon(R.drawable.export)
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                add("PDF").onClick { exportToPDF() }
                add("TXT").onClick { exportToTXT() }
                add("JSON").onClick { exportToJSON() }
                add("HTML").onClick { exportToHTML() }
            }
            .item
    }

    fun MenuItem.onClick(function: () -> Unit) {
        setOnMenuItemClickListener {
            function()
            return@setOnMenuItemClickListener false
        }
    }

    private fun moveNotes(folderTo: Folder) {
        val folderFrom = model.actionMode.getFirstNote().folder
        val ids = model.moveBaseNotes(folderTo)
        Snackbar.make(
                findViewById(R.id.DrawerLayout),
                getQuantityString(folderTo.movedToResId(), ids.size),
                Snackbar.LENGTH_SHORT,
            )
            .apply { setAction(R.string.undo) { model.moveBaseNotes(ids, folderFrom) } }
            .show()
    }

    private fun share() {
        val baseNote = model.actionMode.getFirstNote()
        val body =
            when (baseNote.type) {
                Type.NOTE -> baseNote.body.applySpans(baseNote.spans)
                Type.LIST -> Operations.getBody(baseNote.items)
            }
        Operations.shareNote(this, baseNote.title, body)
    }

    private fun changeColor() {
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.change_color).create()

        val colorAdapter =
            ColorAdapter(
                object : ItemListener {
                    override fun onClick(position: Int) {
                        dialog.dismiss()
                        val color = Color.entries[position]
                        model.colorBaseNote(color)
                    }

                    override fun onLongClick(position: Int) {}
                }
            )

        DialogColorBinding.inflate(layoutInflater).apply {
            RecyclerView.adapter = colorAdapter
            dialog.setView(root)
            dialog.show()
        }
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_selected_notes)
            .setPositiveButton(R.string.delete) { _, _ -> model.deleteSelectedBaseNotes() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun label() {
        val baseNotes = model.actionMode.selectedNotes.values
        lifecycleScope.launch {
            val labels = model.getAllLabels()
            if (labels.isNotEmpty()) {
                displaySelectLabelsDialog(labels, baseNotes)
            } else {
                model.actionMode.close(true)
                navigateWithAnimation(R.id.Labels)
            }
        }
    }

    private fun displaySelectLabelsDialog(labels: Array<String>, baseNotes: Collection<BaseNote>) {
        val checkedPositions =
            labels
                .map { label ->
                    if (baseNotes.all { it.labels.contains(label) }) {
                        TriStateCheckBox.State.CHECKED
                    } else if (baseNotes.any { it.labels.contains(label) }) {
                        TriStateCheckBox.State.PARTIALLY_CHECKED
                    } else {
                        TriStateCheckBox.State.UNCHECKED
                    }
                }
                .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.labels)
            .setNegativeButton(R.string.cancel, null)
            .setMultiChoiceTriStateItems(this, labels, checkedPositions) { idx, state ->
                checkedPositions[idx] = state
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val checkedLabels =
                    checkedPositions.mapIndexedNotNull { index, checked ->
                        if (checked == TriStateCheckBox.State.CHECKED) {
                            labels[index]
                        } else null
                    }
                val uncheckedLabels =
                    checkedPositions.mapIndexedNotNull { index, checked ->
                        if (checked == TriStateCheckBox.State.UNCHECKED) {
                            labels[index]
                        } else null
                    }
                val updatedBaseNotesLabels =
                    baseNotes.map { baseNote ->
                        val noteLabels = baseNote.labels.toMutableList()
                        checkedLabels.forEach { checkedLabel ->
                            if (!noteLabels.contains(checkedLabel)) {
                                noteLabels.add(checkedLabel)
                            }
                        }
                        uncheckedLabels.forEach { uncheckedLabel ->
                            if (noteLabels.contains(uncheckedLabel)) {
                                noteLabels.remove(uncheckedLabel)
                            }
                        }
                        noteLabels
                    }
                baseNotes.zip(updatedBaseNotesLabels).forEach { (baseNote, updatedLabels) ->
                    model.updateBaseNoteLabels(updatedLabels, baseNote.id)
                }
            }
            .show()
    }

    private fun exportToPDF() {
        val baseNote = model.actionMode.getFirstNote()
        model.getPDFFile(
            baseNote,
            object : PostPDFGenerator.OnResult {

                override fun onSuccess(file: File) {
                    showFileOptionsDialog(file, "application/pdf")
                }

                override fun onFailure(message: CharSequence?) {
                    Toast.makeText(
                            this@MainActivity,
                            R.string.something_went_wrong,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
            },
        )
    }

    private fun exportToTXT() {
        val baseNote = model.actionMode.getFirstNote()
        lifecycleScope.launch {
            val file = model.getTXTFile(baseNote)
            showFileOptionsDialog(file, "text/plain")
        }
    }

    private fun exportToJSON() {
        val baseNote = model.actionMode.getFirstNote()
        lifecycleScope.launch {
            val file = model.getJSONFile(baseNote)
            showFileOptionsDialog(file, "application/json")
        }
    }

    private fun exportToHTML() {
        val baseNote = model.actionMode.getFirstNote()
        lifecycleScope.launch {
            val file = model.getHTMLFile(baseNote)
            showFileOptionsDialog(file, "text/html")
        }
    }

    private fun showFileOptionsDialog(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)

        MenuDialog(this)
            .add(R.string.share) { shareFile(uri, mimeType) }
            .add(R.string.view_file) { viewFile(uri, mimeType) }
            .add(R.string.save_to_device) { saveFileToDevice(file, mimeType) }
            .show()
    }

    private fun viewFile(uri: Uri, mimeType: String) {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

        val chooser = Intent.createChooser(intent, getString(R.string.view_note))
        startActivity(chooser)
    }

    private fun shareFile(uri: Uri, mimeType: String) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            }

        val chooser = Intent.createChooser(intent, null)
        startActivity(chooser)
    }

    private fun saveFileToDevice(file: File, mimeType: String) {
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = mimeType
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, file.nameWithoutExtension)
            }
        model.currentFile = file
        exportFileActivityResultLauncher.launch(intent)
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.NavHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        configuration = AppBarConfiguration(binding.NavigationView.menu, binding.DrawerLayout)
        setupActionBarWithNavController(navController, configuration)

        var fragmentIdToLoad: Int? = null
        binding.NavigationView.setNavigationItemSelectedListener { item ->
            fragmentIdToLoad = item.itemId
            binding.DrawerLayout.closeDrawer(GravityCompat.START)
            return@setNavigationItemSelectedListener true
        }

        binding.DrawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {

                override fun onDrawerClosed(drawerView: View) {
                    if (
                        fragmentIdToLoad != null &&
                            navController.currentDestination?.id != fragmentIdToLoad
                    ) {
                        navigateWithAnimation(requireNotNull(fragmentIdToLoad))
                    }
                }
            }
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            fragmentIdToLoad = destination.id
            binding.NavigationView.setCheckedItem(destination.id)
            handleDestinationChange(destination)
        }
    }

    private fun handleDestinationChange(destination: NavDestination) {
        if (destination.id == R.id.Notes) {
            binding.TakeNote.show()
            binding.MakeList.show()
        } else {
            binding.TakeNote.hide()
            binding.MakeList.hide()
        }

        val inputManager = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        if (destination.id == R.id.Search) {
            binding.EnterSearchKeyword.apply {
                setText("")
                visibility = View.VISIBLE
                requestFocus()
                inputManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            binding.EnterSearchKeyword.apply {
                visibility = View.GONE
                inputManager?.hideSoftInputFromWindow(this.windowToken, 0)
            }
        }
    }

    private fun navigateWithAnimation(id: Int) {
        val options = navOptions {
            launchSingleTop = true
            anim {
                exit = androidx.navigation.ui.R.anim.nav_default_exit_anim
                enter = androidx.navigation.ui.R.anim.nav_default_enter_anim
                popExit = androidx.navigation.ui.R.anim.nav_default_pop_exit_anim
                popEnter = androidx.navigation.ui.R.anim.nav_default_pop_enter_anim
            }
            popUpTo(navController.graph.startDestination) { inclusive = false }
        }
        navController.navigate(id, null, options)
    }

    private fun setupSearch() {
        binding.EnterSearchKeyword.apply {
            setText(model.keyword)
            doAfterTextChanged { text -> model.keyword = requireNotNull(text).trim().toString() }
        }
    }

    private fun setupActivityResultLaunchers() {
        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> model.writeCurrentFileToUri(uri) }
                }
            }
    }
}
