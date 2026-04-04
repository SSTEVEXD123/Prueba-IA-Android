package com.jarvis.app.ui.files

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.jarvis.app.R
import com.jarvis.app.databinding.FragmentFilesBinding
import com.jarvis.app.utils.FileInfo
import com.jarvis.app.utils.FileManager
import com.jarvis.app.utils.PdfGenerator
import kotlinx.coroutines.launch

/**
 * FilesFragment
 * Explorador de archivos de JarvisApp:
 * - Listar archivos en /JarvisApp/files/
 * - Crear, abrir, eliminar archivos de texto
 * - Generar PDFs
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private lateinit var fileAdapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onOpen = { file -> openFile(file) },
            onDelete = { file -> confirmDelete(file) },
            onShare = { file -> shareFile(file) }
        )
        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }
    }

    private fun setupFab() {
        binding.fabCreateFile.setOnClickListener { showCreateFileDialog() }
        binding.fabCreatePdf.setOnClickListener { showCreatePdfDialog() }
    }

    private fun loadFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = FileManager.listFiles()
            result.onSuccess { files ->
                fileAdapter.submitList(files)
                binding.textViewEmpty.visibility =
                    if (files.isEmpty()) View.VISIBLE else View.GONE
                binding.textViewFileCount.text = "${files.size} archivo(s)"
            }
        }
    }

    private fun openFile(file: FileInfo) {
        val bundle = Bundle().apply {
            putString("filePath", file.path)
            putString("fileName", file.name)
        }
        findNavController().navigate(R.id.action_files_to_editor, bundle)
    }

    private fun confirmDelete(file: FileInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar archivo")
            .setMessage("¿Eliminar \"${file.name}\"? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    FileManager.deleteFile(file.path).onSuccess {
                        Toast.makeText(requireContext(), "Archivo eliminado", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    }.onFailure {
                        Toast.makeText(requireContext(), "Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun shareFile(file: FileInfo) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            java.io.File(file.path)
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Compartir ${file.name}"))
    }

    // ─── Diálogos ───

    private fun showCreateFileDialog() {
        val inputView = layoutInflater.inflate(R.layout.dialog_input, null)
        val titleInput = inputView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val contentInput = inputView.findViewById<TextInputEditText>(R.id.editTextContent)
        titleInput.hint = "Nombre del archivo (ej: notas.txt)"
        contentInput.hint = "Contenido del archivo…"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📄 Nuevo archivo de texto")
            .setView(inputView)
            .setPositiveButton("Crear") { _, _ ->
                val name = titleInput.text?.toString()?.trim() ?: return@setPositiveButton
                val content = contentInput.text?.toString() ?: ""
                if (name.isEmpty()) return@setPositiveButton

                viewLifecycleOwner.lifecycleScope.launch {
                    FileManager.createTextFile(name, content).onSuccess {
                        Toast.makeText(requireContext(), "Archivo creado: $name", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    }.onFailure {
                        Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCreatePdfDialog() {
        val inputView = layoutInflater.inflate(R.layout.dialog_input, null)
        val titleInput = inputView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val contentInput = inputView.findViewById<TextInputEditText>(R.id.editTextContent)
        titleInput.hint = "Título del PDF"
        contentInput.hint = "Contenido (soporta Markdown: # título, **negrita**, - listas)"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📄 Generar PDF")
            .setView(inputView)
            .setPositiveButton("Generar") { _, _ ->
                val title = titleInput.text?.toString()?.trim() ?: return@setPositiveButton
                val content = contentInput.text?.toString() ?: ""
                if (title.isEmpty()) return@setPositiveButton

                viewLifecycleOwner.lifecycleScope.launch {
                    PdfGenerator.generateFromText(title, content).onSuccess { path ->
                        Toast.makeText(requireContext(), "PDF generado ✓", Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(requireContext(), "Error al generar PDF: ${it.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
