package com.jarvis.app.ui.files

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.jarvis.app.databinding.FragmentFileEditorBinding
import com.jarvis.app.utils.FileManager
import kotlinx.coroutines.launch

/**
 * FileEditorFragment
 * Editor de texto simple para archivos en /JarvisApp/files/
 * Abre, edita y guarda archivos .txt, .md, .json
 */
class FileEditorFragment : Fragment() {

    private var _binding: FragmentFileEditorBinding? = null
    private val binding get() = _binding!!

    // Argumentos pasados vía Navigation
    private var filePath: String = ""
    private var fileName: String = "nuevo_archivo.txt"

    private var hasUnsavedChanges = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Leer argumentos de navegación
        arguments?.let {
            filePath = it.getString("filePath", "")
            fileName = it.getString("fileName", "nuevo_archivo.txt")
        }

        setupToolbar()
        setupEditor()
        loadFileContent()
    }

    private fun setupToolbar() {
        binding.textViewFileName.text = fileName
        binding.toolbarEditor.setNavigationOnClickListener {
            if (hasUnsavedChanges) {
                showUnsavedChangesDialog()
            } else {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupEditor() {
        binding.editTextFileContent.addTextChangedListener(object :
            android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                hasUnsavedChanges = true
                binding.textViewSaveStatus.text = "Sin guardar"
                binding.textViewSaveStatus.setTextColor(
                    requireContext().getColor(com.jarvis.app.R.color.color_warning)
                )
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Botón guardar
        binding.buttonSave.setOnClickListener { saveFile() }

        // Guardar con Ctrl+S (teclado hardware)
        binding.editTextFileContent.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_S
            ) {
                saveFile()
                true
            } else false
        }

        // Botones de formato rápido
        binding.buttonBold.setOnClickListener { insertMarkdown("**", "**") }
        binding.buttonItalic.setOnClickListener { insertMarkdown("*", "*") }
        binding.buttonH1.setOnClickListener { insertMarkdownLine("# ") }
        binding.buttonH2.setOnClickListener { insertMarkdownLine("## ") }
        binding.buttonList.setOnClickListener { insertMarkdownLine("- ") }
        binding.buttonCode.setOnClickListener { insertMarkdown("`", "`") }
    }

    private fun loadFileContent() {
        if (filePath.isEmpty()) {
            // Archivo nuevo
            binding.editTextFileContent.setText("")
            hasUnsavedChanges = false
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            FileManager.readFile(filePath).onSuccess { content ->
                binding.editTextFileContent.setText(content)
                hasUnsavedChanges = false
                updateSaveStatus(saved = true)

                // Mostrar estadísticas básicas
                val words = content.trim().split(Regex("\\s+")).size
                val lines = content.lines().size
                binding.textViewStats.text = "$lines líneas · $words palabras"
            }.onFailure {
                Toast.makeText(requireContext(), "Error al abrir el archivo: ${it.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveFile() {
        val content = binding.editTextFileContent.text?.toString() ?: ""
        val name = if (fileName.isEmpty()) "archivo.txt" else fileName

        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (filePath.isNotEmpty()) {
                FileManager.updateFile(filePath, content)
            } else {
                FileManager.createTextFile(name, content).map { Unit }
            }

            result.onSuccess {
                hasUnsavedChanges = false
                updateSaveStatus(saved = true)
                Toast.makeText(requireContext(), "Guardado ✓", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(requireContext(), "Error al guardar: ${it.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSaveStatus(saved: Boolean) {
        binding.textViewSaveStatus.text = if (saved) "Guardado ✓" else "Sin guardar"
        binding.textViewSaveStatus.setTextColor(
            requireContext().getColor(
                if (saved) com.jarvis.app.R.color.color_success
                else com.jarvis.app.R.color.color_warning
            )
        )
    }

    private fun insertMarkdown(prefix: String, suffix: String) {
        val et = binding.editTextFileContent
        val start = et.selectionStart
        val end = et.selectionEnd
        val selected = et.text?.substring(start, end) ?: ""
        et.text?.replace(start, end, "$prefix$selected$suffix")
        if (selected.isEmpty()) et.setSelection(start + prefix.length)
    }

    private fun insertMarkdownLine(prefix: String) {
        val et = binding.editTextFileContent
        val pos = et.selectionStart
        et.text?.insert(pos, "\n$prefix")
    }

    private fun showUnsavedChangesDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cambios sin guardar")
            .setMessage("¿Guardar antes de salir?")
            .setPositiveButton("Guardar y salir") { _, _ ->
                saveFile()
                findNavController().navigateUp()
            }
            .setNegativeButton("Descartar") { _, _ ->
                findNavController().navigateUp()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
